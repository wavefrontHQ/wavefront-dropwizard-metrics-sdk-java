package com.wavefront.dropwizard.metrics;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
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
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
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

import static com.wavefront.sdk.common.Constants.DELTA_PREFIX;

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
     * Builds a {@link DropwizardMetricsReporter} with the given properties, sending metrics and
     * histograms directly to a given Wavefront server using either proxy or direct ingestion APIs.
     *
     * @param wavefrontSender Wavefront Sender to send various Wavefront atoms.
     * @return a {@link DropwizardMetricsReporter}
     */
    public DropwizardMetricsReporter build(WavefrontSender wavefrontSender) {
      return new DropwizardMetricsReporter(registry, wavefrontSender, clock, prefix,
          source, reporterPointTags, filter, includeJvmMetrics,
          disabledMetricAttributes, histogramGranularities);
    }
  }

  private final WavefrontSender wavefrontSender;
  private final Clock clock;
  private final String prefix;
  private final String source;
  private final Map<String, String> reporterPointTags;
  private final Set<HistogramGranularity> histogramGranularities;

  private DropwizardMetricsReporter(MetricRegistry registry,
                                    WavefrontSender wavefrontSender,
                                    final Clock clock,
                                    String prefix,
                                    String source,
                                    Map<String, String> reporterPointTags,
                                    MetricFilter filter,
                                    boolean includeJvmMetrics,
                                    Set<MetricAttribute> disabledMetricAttributes,
                                    Set<HistogramGranularity> histogramGranularities) {
    super(registry, "wavefront-reporter", filter, TimeUnit.SECONDS, TimeUnit.MILLISECONDS,
        Executors.newSingleThreadScheduledExecutor(), true,
        disabledMetricAttributes == null ? Collections.emptySet() : disabledMetricAttributes);
    this.wavefrontSender = wavefrontSender;
    this.clock = clock;
    this.prefix = prefix;
    this.source = source;
    this.reporterPointTags = reporterPointTags;
    this.histogramGranularities = histogramGranularities;

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
      logger.log(Level.INFO, "Metric with the same name already exists", e);
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
    final Snapshot snapshot = timer.getSnapshot();
    final long time = clock.getTime() / 1000;
    sendIfEnabled(MetricAttribute.MAX, name, convertDuration(snapshot.getMax()), time);
    sendIfEnabled(MetricAttribute.MEAN, name, convertDuration(snapshot.getMean()), time);
    sendIfEnabled(MetricAttribute.MIN, name, convertDuration(snapshot.getMin()), time);
    sendIfEnabled(MetricAttribute.STDDEV, name, convertDuration(snapshot.getStdDev()), time);
    sendIfEnabled(MetricAttribute.P50, name, convertDuration(snapshot.getMedian()), time);
    sendIfEnabled(MetricAttribute.P75, name, convertDuration(snapshot.get75thPercentile()), time);
    sendIfEnabled(MetricAttribute.P95, name, convertDuration(snapshot.get95thPercentile()), time);
    sendIfEnabled(MetricAttribute.P98, name, convertDuration(snapshot.get98thPercentile()), time);
    sendIfEnabled(MetricAttribute.P99, name, convertDuration(snapshot.get99thPercentile()), time);
    sendIfEnabled(MetricAttribute.P999, name, convertDuration(snapshot.get999thPercentile()), time);

    reportMetered(name, timer);
  }

  private void reportMetered(String name, Metered meter) throws IOException {
    final long time = clock.getTime() / 1000;
    sendIfEnabled(MetricAttribute.COUNT, name, meter.getCount(), time);
    sendIfEnabled(MetricAttribute.M1_RATE, name, convertRate(meter.getOneMinuteRate()), time);
    sendIfEnabled(MetricAttribute.M5_RATE, name, convertRate(meter.getFiveMinuteRate()), time);
    sendIfEnabled(MetricAttribute.M15_RATE, name, convertRate(meter.getFifteenMinuteRate()), time);
    sendIfEnabled(MetricAttribute.MEAN_RATE, name, convertRate(meter.getMeanRate()), time);
  }

  private void reportHistogram(String name, Histogram histogram) throws IOException {
    if (histogram instanceof WavefrontHistogram) {
      String histogramName = prefixAndSanitize(name);
      for (WavefrontHistogramImpl.Distribution distribution :
          ((WavefrontHistogram) histogram).flushDistributions()) {
        wavefrontSender.sendDistribution(histogramName, distribution.centroids,
            histogramGranularities, distribution.timestamp, source, reporterPointTags);
      }
    } else {
      final Snapshot snapshot = histogram.getSnapshot();
      final long time = clock.getTime() / 1000;
      sendIfEnabled(MetricAttribute.COUNT, name, histogram.getCount(), time);
      sendIfEnabled(MetricAttribute.MAX, name, snapshot.getMax(), time);
      sendIfEnabled(MetricAttribute.MEAN, name, snapshot.getMean(), time);
      sendIfEnabled(MetricAttribute.MIN, name, snapshot.getMin(), time);
      sendIfEnabled(MetricAttribute.STDDEV, name, snapshot.getStdDev(), time);
      sendIfEnabled(MetricAttribute.P50, name, snapshot.getMedian(), time);
      sendIfEnabled(MetricAttribute.P75, name, snapshot.get75thPercentile(), time);
      sendIfEnabled(MetricAttribute.P95, name, snapshot.get95thPercentile(), time);
      sendIfEnabled(MetricAttribute.P98, name, snapshot.get98thPercentile(), time);
      sendIfEnabled(MetricAttribute.P99, name, snapshot.get99thPercentile(), time);
      sendIfEnabled(MetricAttribute.P999, name, snapshot.get999thPercentile(), time);
    }
  }

  private void reportCounter(String name, Counter counter) throws IOException {
    if (counter instanceof DeltaCounter) {
      long count = counter.getCount();
      name = DELTA_PREFIX + prefixAndSanitize(name.substring(1), "count");
      wavefrontSender.sendDeltaCounter(name, count, source, reporterPointTags);
      counter.dec(count);
    } else {
      wavefrontSender.sendMetric(prefixAndSanitize(name, "count"), counter.getCount(),
          clock.getTime() / 1000, source, reporterPointTags);
    }
  }

  private void reportGauge(String name, Gauge<Number> gauge) throws IOException {
    wavefrontSender.sendMetric(prefixAndSanitize(name), gauge.getValue().doubleValue(),
        clock.getTime() / 1000, source, reporterPointTags);
  }

  private void sendIfEnabled(MetricAttribute type, String name, double value, long timestamp)
      throws IOException {
    if (!getDisabledMetricAttributes().contains(type)) {
      wavefrontSender.sendMetric(prefixAndSanitize(name, type.getCode()), value, timestamp,
          source, reporterPointTags);
    }
  }

  private String prefixAndSanitize(String... components) {
    return sanitize(MetricRegistry.name(prefix, components));
  }

  private static String sanitize(String name) {
    return SIMPLE_NAMES.matcher(name).replaceAll("_");
  }

  private static final Pattern SIMPLE_NAMES = Pattern.compile("[^a-zA-Z0-9_.\\-~]");
}
