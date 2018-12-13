package com.wavefront.dropwizard.metrics;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.CustomTimeUnitMeter;
import com.codahale.metrics.CustomTimeUnitTimer;
import com.codahale.metrics.DeltaCounter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.WavefrontHistogram;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.wavefront.dropwizard.metrics.TaggedMetricName.decode;
import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.DELTA_PREFIX;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;

/**
 * A reporter which publishes metric values to a Wavefront cluster via proxy or direct ingestion
 * from a Dropwizard {@link MetricRegistry}.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class DropwizardMetricsReporter extends ScheduledReporter {

  private static final Logger logger =
      Logger.getLogger(DropwizardMetricsReporter.class.getCanonicalName());

  /**
   * Returns a new {@link Builder} for {@link DropwizardMetricsReporter}.
   *
   * @param registry the registry to report
   * @return a {@link Builder} instance for a {@link DropwizardMetricsReporter}
   */
  public static Builder forRegistry(MetricRegistry registry) {
    return new Builder(registry);
  }

  /**
   * A builder for {@link DropwizardMetricsReporter} instances. Defaults to not using a
   * prefix, using the default clock, converting rates to events/second, converting durations to
   * milliseconds, a host named "dropwizard-metrics", no point Tags, and not filtering any metrics.
   */
  public static class Builder {
    private final MetricRegistry registry;
    private Clock clock;
    private String prefix;
    private MetricFilter filter;
    private String source;
    private final Map<String, String> reporterPointTags;
    private boolean includeJvmMetrics;
    private Set<MetricAttribute> disabledMetricAttributes;
    private final Set<HistogramGranularity> histogramGranularities;
    private boolean reportHistogramSum;
    private MetricAttributeCodeMapper metricAttributeCodeMapper;
    private boolean ignoreZeroCounters;
    private boolean ignoreEmptyHistograms;

    private Builder(MetricRegistry registry) {
      this.registry = registry;
      this.clock = Clock.defaultClock();
      this.prefix = null;
      this.filter = MetricFilter.ALL;
      this.source = "dropwizard-metrics";
      this.reporterPointTags = new HashMap<>();
      this.includeJvmMetrics = false;
      this.disabledMetricAttributes = Collections.emptySet();
      this.histogramGranularities = new HashSet<>();
      this.reportHistogramSum = false;
      this.metricAttributeCodeMapper = (metric, code) -> code;
      this.ignoreZeroCounters = false;
      this.ignoreEmptyHistograms = false;
    }

    /**
     * Use the given {@link Clock} instance for the time. Defaults to Clock.defaultClock()
     *
     * @param clock a {@link Clock} instance
     * @return {@code this}
     */
    public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Prefix all metric names with the given string. Defaults to null.
     *
     * @param prefix the prefix for all metric names
     * @return {@code this}
     */
    public Builder prefixedWith(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * Set the source for this reporter. This is equivalent to withHost.
     *
     * @param source the host for all metrics
     * @return {@code this}
     */
    public Builder withSource(String source) {
      this.source = source;
      return this;
    }

    /**
     * Add application tags to reporterPointTags.
     *
     * @param applicationTags application metadata.
     * @return {@code this}
     */
    public Builder withApplicationTags(ApplicationTags applicationTags) {
      this.reporterPointTags.put(APPLICATION_TAG_KEY, applicationTags.getApplication());
      this.reporterPointTags.put(SERVICE_TAG_KEY, applicationTags.getService());
      this.reporterPointTags.put(CLUSTER_TAG_KEY,
          applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster());
      this.reporterPointTags.put(SHARD_TAG_KEY,
          applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
      if (applicationTags.getCustomTags() != null) {
        this.reporterPointTags.putAll(applicationTags.getCustomTags());
      }
      return this;
    }

    /**
     * Set the Point Tags for this reporter.
     *
     * @param reporterPointTags the pointTags Map for all metrics
     * @return {@code this}
     */
    public Builder withReporterPointTags(Map<String, String> reporterPointTags) {
      this.reporterPointTags.putAll(reporterPointTags);
      return this;
    }

    /**
     * Set a point tag for this reporter.
     *
     * @param tagKey the key of the Point Tag
     * @param tagVal the value of the Point Tag
     * @return {@code this}
     */
    public Builder withReporterPointTag(String tagKey, String tagVal) {
      this.reporterPointTags.put(tagKey, tagVal);
      return this;
    }

    /**
     * Only report metrics which match the given filter. Defaults to MetricFilter.ALL
     *
     * @param filter a {@link MetricFilter}
     * @return {@code this}
     */
    public Builder filter(MetricFilter filter) {
      this.filter = filter;
      return this;
    }

    /**
     * Don't report the passed metric attributes for all metrics (e.g. "p999", "stddev" or "m15").
     * See {@link MetricAttribute}.
     *
     * @param disabledMetricAttributes a set of {@link MetricAttribute}
     * @return {@code this}
     */
    public Builder disabledMetricAttributes(Set<MetricAttribute> disabledMetricAttributes) {
      this.disabledMetricAttributes = disabledMetricAttributes;
      return this;
    }

    /**
     * Include JVM Metrics from this Reporter.
     *
     * @return {@code this}
     */
    public Builder withJvmMetrics() {
      this.includeJvmMetrics = true;
      return this;
    }

    /**
     * Report histogram distributions aggregated into minute intervals
     *
     * @return {@code this}
     */
    public Builder reportMinuteDistribution() {
      this.histogramGranularities.add(HistogramGranularity.MINUTE);
      return this;
    }

    /**
     * Report histogram distributions aggregated into hour intervals
     *
     * @return {@code this}
     */
    public Builder reportHourDistribution() {
      this.histogramGranularities.add(HistogramGranularity.HOUR);
      return this;
    }

    /**
     * Report histogram distributions aggregated into day intervals
     *
     * @return {@code this}
     */
    public Builder reportDayDistribution() {
      this.histogramGranularities.add(HistogramGranularity.DAY);
      return this;
    }

    /**
     * Report a sum for each histogram distribution
     *
     * @return {@code this}
     */
    public Builder reportHistogramSum() {
      this.reportHistogramSum = true;
      return this;
    }

    /**
     * Map metric names using the provided {@link MetricAttributeCodeMapper} for reporting.
     *
     * @return {@code this}
     */
    public Builder withMetricAttributeCodeMapper(MetricAttributeCodeMapper mapper) {
      this.metricAttributeCodeMapper = mapper;
      return this;
    }

    /**
     * Do not report counters that have a value of zero.
     *
     * @return {@code this}
     */
    public Builder ignoreZeroCounters() {
      this.ignoreZeroCounters = true;
      return this;
    }

    /**
     * Do not report empty histogram snapshots (except for a count).
     *
     * @return {@code this}
     */
    public Builder ignoreEmptyHistograms() {
      this.ignoreEmptyHistograms = true;
      return this;
    }

    /**
     * Builds a {@link DropwizardMetricsReporter} with the given properties, sending metrics and
     * histograms directly to a given Wavefront server using either proxy or direct ingestion APIs.
     *
     * @param wavefrontSender Wavefront Sender to send various Wavefront atoms.
     * @return a {@link DropwizardMetricsReporter}
     */
    public DropwizardMetricsReporter build(WavefrontSender wavefrontSender) {
      return new DropwizardMetricsReporter(registry, wavefrontSender, clock, prefix,
          source, reporterPointTags, filter, includeJvmMetrics,
          disabledMetricAttributes, histogramGranularities, reportHistogramSum,
          metricAttributeCodeMapper, ignoreZeroCounters, ignoreEmptyHistograms);
    }
  }

  /**
   * Interface for mapping metrics and their metric attribute suffixes to custom metric names
   * that are reported to Wavefront.
   */
  @FunctionalInterface
  public interface MetricAttributeCodeMapper {
    String map(Metric metric, String metricAttributeCode);
  }

  private final WavefrontSender wavefrontSender;
  private final Clock clock;
  private final String prefix;
  private final String source;
  private final Map<String, String> reporterPointTags;
  private final Set<HistogramGranularity> histogramGranularities;
  private final boolean reportHistogramSum;
  private final MetricAttributeCodeMapper metricAttributeCodeMapper;
  private final boolean ignoreZeroCounters;
  private final boolean ignoreEmptyHistograms;

  private DropwizardMetricsReporter(MetricRegistry registry,
                                    WavefrontSender wavefrontSender,
                                    final Clock clock,
                                    String prefix,
                                    String source,
                                    Map<String, String> reporterPointTags,
                                    MetricFilter filter,
                                    boolean includeJvmMetrics,
                                    Set<MetricAttribute> disabledMetricAttributes,
                                    Set<HistogramGranularity> histogramGranularities,
                                    boolean reportHistogramSum,
                                    MetricAttributeCodeMapper mapper,
                                    boolean ignoreZeroCounters,
                                    boolean ignoreEmptyHistograms) {
    super(registry, "wavefront-reporter", filter, TimeUnit.SECONDS, TimeUnit.MILLISECONDS,
        Executors.newSingleThreadScheduledExecutor(), true,
        disabledMetricAttributes == null ? Collections.emptySet() : disabledMetricAttributes);
    this.wavefrontSender = wavefrontSender;
    this.clock = clock;
    this.prefix = prefix;
    this.source = source;
    this.reporterPointTags = reporterPointTags;
    this.histogramGranularities = histogramGranularities;
    this.reportHistogramSum = reportHistogramSum;
    this.metricAttributeCodeMapper = mapper == null ? (metric, code) -> code : mapper;
    this.ignoreZeroCounters = ignoreZeroCounters;
    this.ignoreEmptyHistograms = ignoreEmptyHistograms;

    if (includeJvmMetrics) {
      tryRegister(registry, "jvm.uptime",
          (Gauge<Long>) () -> ManagementFactory.getRuntimeMXBean().getUptime());
      tryRegister(registry, "jvm.current_time", (Gauge<Long>) clock::getTime);
      tryRegister(registry, "jvm.classes", new ClassLoadingGaugeSet());
      tryRegister(registry, "jvm.fd_usage", new FileDescriptorRatioGauge());
      tryRegister(registry, "jvm.buffers",
          new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
      tryRegister(registry, "jvm.gc", new GarbageCollectorMetricSet());
      tryRegister(registry, "jvm.memory", new MemoryUsageGaugeSet());
      tryRegister(registry, "jvm.thread-states", new ThreadStatesGaugeSet());
    }
  }

  private <T extends Metric> void tryRegister(MetricRegistry registry, String name, T metric) {
    // Dropwizard services automatically include JVM metrics, so adding them again would throw an exception
    try {
      registry.register(name, metric);
    } catch (IllegalArgumentException e) {
      logger.log(Level.INFO, e.getMessage());
    }
  }

  @Override
  public void report(SortedMap<String, Gauge> gauges,
                     SortedMap<String, Counter> counters,
                     SortedMap<String, Histogram> histograms,
                     SortedMap<String, Meter> meters,
                     SortedMap<String, Timer> timers) {
    try {
      for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
        if (entry.getValue().getValue() instanceof Number) {
          reportGauge(entry.getKey(), entry.getValue());
        }
      }

      for (Map.Entry<String, Counter> entry : counters.entrySet()) {
        reportCounter(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
        reportHistogram(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Meter> entry : meters.entrySet()) {
        reportMetered(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Timer> entry : timers.entrySet()) {
        reportTimer(entry.getKey(), entry.getValue());
      }

    } catch (IOException e) {
      logger.log(Level.WARNING,"Unable to report to Wavefront", e);
      try {
        wavefrontSender.close();
      } catch (IOException e1) {
        logger.log(Level.WARNING, "Error closing Wavefront", e1);
      }
    }
  }

  @Override
  public void stop() {
    try {
      super.stop();
    } finally {
      try {
        wavefrontSender.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "Error disconnecting from Wavefront", e);
      }
    }
  }

  /**
   * Get total failure count reported by this reporter
   *
   * @return total failure count
   */
  public int getFailureCount() {
    return wavefrontSender.getFailureCount();
  }

  private void reportTimer(String name, Timer timer) throws IOException {
    final TaggedMetricName taggedMetricName = decode(name);
    final Map<String, String> tags = aggregateTags(taggedMetricName.getTags());
    final Snapshot snapshot = timer.getSnapshot();
    final long time = clock.getTime() / 1000;
    sendIfEnabled(timer, MetricAttribute.MAX, taggedMetricName, convertDuration(snapshot.getMax(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.MEAN, taggedMetricName, convertDuration(snapshot.getMean(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.MIN, taggedMetricName, convertDuration(snapshot.getMin(), timer), time, tags);
    if (reportHistogramSum) {
      final String code = metricAttributeCodeMapper.map(timer, "sum");
      wavefrontSender.sendMetric(prefixAndSanitize(taggedMetricName.getGroup(),
              taggedMetricName.getName(), code),
              convertDuration(Arrays.stream(snapshot.getValues()).sum(), timer), time, source,
              tags);
    }
    sendIfEnabled(timer, MetricAttribute.STDDEV, taggedMetricName, convertDuration(snapshot.getStdDev(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P50, taggedMetricName, convertDuration(snapshot.getMedian(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P75, taggedMetricName, convertDuration(snapshot.get75thPercentile(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P95, taggedMetricName, convertDuration(snapshot.get95thPercentile(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P98, taggedMetricName, convertDuration(snapshot.get98thPercentile(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P99, taggedMetricName, convertDuration(snapshot.get99thPercentile(), timer), time, tags);
    sendIfEnabled(timer, MetricAttribute.P999, taggedMetricName, convertDuration(snapshot.get999thPercentile(), timer), time, tags);

    reportMetered(name, timer);
  }

  private void reportMetered(String name, Metered meter) throws IOException {
    final TaggedMetricName taggedMetricName = decode(name);
    final Map<String, String> tags = aggregateTags(taggedMetricName.getTags());
    final long time = clock.getTime() / 1000;
    sendIfEnabled(meter, MetricAttribute.COUNT, taggedMetricName, meter.getCount(), time, tags);
    sendIfEnabled(meter, MetricAttribute.M1_RATE, taggedMetricName, convertRate(meter.getOneMinuteRate(), meter), time, tags);
    sendIfEnabled(meter, MetricAttribute.M5_RATE, taggedMetricName, convertRate(meter.getFiveMinuteRate(), meter), time, tags);
    sendIfEnabled(meter, MetricAttribute.M15_RATE, taggedMetricName, convertRate(meter.getFifteenMinuteRate(), meter), time, tags);
    sendIfEnabled(meter, MetricAttribute.MEAN_RATE, taggedMetricName, convertRate(meter.getMeanRate(), meter), time, tags);
  }

  private void reportHistogram(String name, Histogram histogram) throws IOException {
    final TaggedMetricName taggedMetricName = decode(name);
    final Map<String, String> tags = aggregateTags(taggedMetricName.getTags());
    if (histogram instanceof WavefrontHistogram) {
      String histogramName =
              prefixAndSanitize(taggedMetricName.getGroup(), taggedMetricName.getName());
      for (WavefrontHistogramImpl.Distribution distribution :
          ((WavefrontHistogram) histogram).flushDistributions()) {
        wavefrontSender.sendDistribution(histogramName, distribution.centroids,
            histogramGranularities, distribution.timestamp, source, tags);
      }
    } else {
      final Snapshot snapshot = histogram.getSnapshot();
      final long count = histogram.getCount();
      final long time = clock.getTime() / 1000;
      if (ignoreEmptyHistograms && count == 0) {
        // send count still but skip the others.
        sendIfEnabled(histogram, MetricAttribute.COUNT, taggedMetricName, count, time, tags);
      } else {
        sendIfEnabled(histogram, MetricAttribute.COUNT, taggedMetricName, count, time, tags);
        sendIfEnabled(histogram, MetricAttribute.MAX, taggedMetricName, snapshot.getMax(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.MEAN, taggedMetricName, snapshot.getMean(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.MIN, taggedMetricName, snapshot.getMin(), time, tags);
        if (reportHistogramSum) {
          final String code = metricAttributeCodeMapper.map(histogram, "sum");
          wavefrontSender.sendMetric(prefixAndSanitize(taggedMetricName.getGroup(),
                  taggedMetricName.getName(), code), Arrays.stream(snapshot.getValues()).sum(), time,
                  source, tags);
        }
        sendIfEnabled(histogram, MetricAttribute.STDDEV, taggedMetricName, snapshot.getStdDev(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P50, taggedMetricName, snapshot.getMedian(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P75, taggedMetricName, snapshot.get75thPercentile(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P95, taggedMetricName, snapshot.get95thPercentile(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P98, taggedMetricName, snapshot.get98thPercentile(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P99, taggedMetricName, snapshot.get99thPercentile(), time, tags);
        sendIfEnabled(histogram, MetricAttribute.P999, taggedMetricName, snapshot.get999thPercentile(), time, tags);
      }
    }
  }

  private void reportCounter(String name, Counter counter) throws IOException {
    final long count = counter.getCount();
    if (ignoreZeroCounters && count == 0) return;

    final TaggedMetricName taggedMetricName = decode(name);
    final Map<String, String> tags = aggregateTags(taggedMetricName.getTags());
    final String code = metricAttributeCodeMapper.map(counter, "count");
    if (counter instanceof DeltaCounter) {
      name = DELTA_PREFIX + prefixAndSanitize(taggedMetricName.getGroup(),
              taggedMetricName.getName().substring(1), code);
      wavefrontSender.sendDeltaCounter(name, count, source, tags);
      counter.dec(count);
    } else {
      name = prefixAndSanitize(taggedMetricName.getGroup(), taggedMetricName.getName(), code);
      wavefrontSender.sendMetric(name, count, clock.getTime() / 1000, source, tags);
    }
  }

  private void reportGauge(String name, Gauge<Number> gauge) throws IOException {
    final TaggedMetricName taggedMetricName = decode(name);
    final Map<String, String> tags = aggregateTags(taggedMetricName.getTags());
    final String code = metricAttributeCodeMapper.map(gauge, "");
    name = prefixAndSanitize(taggedMetricName.getGroup(), taggedMetricName.getName(), code);
    wavefrontSender.sendMetric(name, gauge.getValue().doubleValue(), clock.getTime() / 1000,
            source, tags);
  }

  private void sendIfEnabled(Metric metric, MetricAttribute type, TaggedMetricName taggedMetricName,
                             double value, long timestamp, Map<String, String> tags)
      throws IOException {
    if (!getDisabledMetricAttributes().contains(type)) {
      final String code = metricAttributeCodeMapper.map(metric, type.getCode());
      final String name =
              prefixAndSanitize(taggedMetricName.getGroup(), taggedMetricName.getName(), code);
      wavefrontSender.sendMetric(name, value, timestamp, source, tags);
    }
  }

  private String prefixAndSanitize(String... components) {
    return sanitize(MetricRegistry.name(prefix, components));
  }

  private static String sanitize(String name) {
    return SIMPLE_NAMES.matcher(name).replaceAll("_");
  }

  private Map<String, String> aggregateTags(Map<String, String> pointTags) {
    return Stream.of(pointTags, reporterPointTags).flatMap(map -> map.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2));
  }

  private double convertDuration(double duration, Timer timer) {
    if (timer instanceof CustomTimeUnitTimer) {
      return ((CustomTimeUnitTimer) timer).convertDuration(duration);
    } else {
      return convertDuration(duration);
    }
  }

  private double convertRate(double rate, Metered meter) {
    if (meter instanceof CustomTimeUnitMeter) {
      return ((CustomTimeUnitMeter) meter).convertRate(rate);
    } else if (meter instanceof CustomTimeUnitTimer) {
      return ((CustomTimeUnitTimer) meter).convertRate(rate);
    } else {
      return convertRate(rate);
    }
  }

  private static final Pattern SIMPLE_NAMES = Pattern.compile("[^a-zA-Z0-9_.\\-~]");
}
