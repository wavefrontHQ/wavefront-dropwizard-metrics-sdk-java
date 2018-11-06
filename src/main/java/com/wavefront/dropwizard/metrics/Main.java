package com.wavefront.dropwizard.metrics;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.DeltaCounter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.WavefrontHistogram;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Driver class for ad-hoc experiments
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Main {

  public static void main(String[] args) throws InterruptedException, IOException {
    String wavefrontServer = args[0];
    String token = args[1];
    String proxyHost = args.length < 3 ? null : args[2];
    String metricsPort = args.length < 4 ? null : args[3];
    String distributionPort = args.length < 5 ? null : args[4];

    WavefrontProxyClient.Builder proxyBuilder = new WavefrontProxyClient.Builder(proxyHost);
    if (metricsPort != null) {
      proxyBuilder.metricsPort(Integer.parseInt(metricsPort));
    }
    if (distributionPort != null) {
      proxyBuilder.distributionPort(Integer.parseInt(distributionPort));
    }
    WavefrontProxyClient wavefrontProxyClient = proxyBuilder.build();

    WavefrontDirectIngestionClient wavefrontDirectIngestionClient =
        new WavefrontDirectIngestionClient.Builder(wavefrontServer, token).build();

    MetricRegistry metricRegistry = new MetricRegistry();
    DropwizardMetricsReporter.Builder builder =
        DropwizardMetricsReporter.forRegistry(metricRegistry);

    /* Set the source for your metrics and histograms */
    builder.withSource("mySource");

    /* Invoke this method to report your metrics and histograms with given prefix */
    builder.prefixedWith("myPrefix");

    /* Explicitly set the clock */
    builder.withClock(new Clock() {
      @Override
      public long getTick() {
        return System.currentTimeMillis();
      }
    });

    /* Optional: Set ApplicationTags to propagate application metadata to the reported metrics */
    builder.withApplicationTags(new ApplicationTags.Builder("OrderingApp", "Inventory").
        cluster("us-west-1").shard("primary").
        customTags(new HashMap<String, String>(){{
          put("env", "Staging");
          put("location", "SF"); }}).build());

    /* Set reporter level point tags map for your metrics and histograms */
    builder.withReporterPointTags(new HashMap<String, String>() {{
      put("env", "Staging");
      put("location", "SF");
    }});

    /* Add a specific reporter level point tag key value for your metrics and histograms */
    builder.withReporterPointTag("cluster", "us-west");

    /* Only report metrics that begin with 'my*'  */
    builder.filter(MetricFilter.startsWith("my"));

    /* Don't report stddev and m15 */
    Set<MetricAttribute> set = new HashSet<>();
    set.add(MetricAttribute.STDDEV);
    set.add(MetricAttribute.M15_RATE);
    builder.disabledMetricAttributes(set);

    /* Invoke this method if you want to report JVM metrics for your Java app */
    builder.withJvmMetrics();

    /* Invoke this method if you want to report minute bin Wavefront histograms */
    builder.reportMinuteDistribution();

    /* Invoke this method if you want to report hour bin Wavefront histograms  */
    builder.reportHourDistribution();

    /* Invoke this method if you want to report day bin Wavefront histograms */
    builder.reportDayDistribution();

    DropwizardMetricsReporter dropwizardMetricsReporter =
        builder.build(wavefrontDirectIngestionClient);

    /*
     * Instead of direct ingestion, you can also report the metrics and histograms to Wavefront via
     * proxy using the below line of code
     */
    //DropwizardMetricsReporter dropwizardMetricsReporter = builder.build(wavefrontProxyClient);

    /* Report metrics and histograms to Wavefront every 30 seconds */
    dropwizardMetricsReporter.start(30, TimeUnit.SECONDS);

    /* 'notMyCounter' won't be reported as it does not begin with prefix - 'my*'  */
    Counter notReported = metricRegistry.counter("notMyCounter");
    Counter counter = metricRegistry.counter("myCounter");
    DeltaCounter deltaCounter = DeltaCounter.get(metricRegistry, "myDeltaCounter");
    AtomicInteger bufferSize = new AtomicInteger();
    Gauge gauge = metricRegistry.register("myGauge", () -> bufferSize.get());
    Meter meter = metricRegistry.meter("myMeter");
    Timer timer = metricRegistry.timer("myTimer");
    Histogram dropwizardHistogram = metricRegistry.histogram("myDropwizardHistogram");
    WavefrontHistogram wavefrontHistogram =
        WavefrontHistogram.get(metricRegistry, "myWavefrontHistogram");

    for (int i = 0; i < 50; i++) {
      counter.inc();
      deltaCounter.inc();
      notReported.inc();
      bufferSize.set(10 * i);
      meter.mark(i);
      timer.update(i, TimeUnit.SECONDS);
      dropwizardHistogram.update(i);
      wavefrontHistogram.update(i);
      wavefrontHistogram.update(i * 1.0);
      Thread.sleep(50);
    }
  }
}
