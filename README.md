# Wavefront Dropwizard Metrics Reporter [![travis build status](https://travis-ci.com/wavefrontHQ/wavefront-dropwizard-metrics.svg?branch=master)](https://travis-ci.com/wavefrontHQ/wavefront-dropwizard-metrics)

This library provides support for reporting Dropwizard metrics and histograms to Wavefront via proxy or direct ingestion.

## Usage
If you are using Maven, add following maven dependency to your pom.xml
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>dropwizard-metrics-sdk</artifactId>
    <version>0.9.0</version>
</dependency>
```

### Construct DropwizardMetricsReporter.Builder
```java
  MetricRegistry metricRegistry = new MetricRegistry();
  DropwizardMetricsReporter.Builder builder = DropwizardMetricsReporter.forRegistry(metricRegistry);

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

  /* Set reporter level point tags map for your metrics and histograms */
  builder.withReporterPointTags(ImmutableMap.<String, String>builder().
          put("env", "Staging").
          put("location", "SF").build());

  /* Add a specific reporter level point tag key value for your metrics and histograms */
  builder.withReporterPointTag("cluster", "us-west");

  /* Only report metrics that begin with 'my*'  */
  builder.filter(MetricFilter.startsWith("my"));

  /* Don't report stddev and m15 */
  builder.disabledMetricAttributes(ImmutableSet.<MetricAttribute>builder().
          add(MetricAttribute.STDDEV).add(MetricAttribute.M15_RATE).build());

  /* Invoke this method if you want to report JVM metrics for your Java app */
  builder.withJvmMetrics();

  /* Invoke this method if you want to report minute bin Wavefront histograms */
  builder.reportMinuteDistribution();

  /* Invoke this method if you want to report hour bin Wavefront histograms  */
  builder.reportHourDistribution();

  /* Invoke this method if you want to report day bin Wavefront histograms */
  builder.reportDayDistribution();
```

### WavefrontSender
We need to instantiate WavefrontSender 
(i.e. either WavefrontProxyClient or WavefrontDirectIngestionClient)
Refer to this page (https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md)
to instantiate WavefrontProxyClient or WavefrontDirectIngestionClient.

### Option 1 - Report Dropwizard metrics and histograms to Wavefront via Proxy
```java
  /*
   * Using the 
   * 1) DropwizardMetricsReporter.Builder constructed above, and
   * 2) wavefrontProxyClient instantiated using above instructions
   * report metrics and histograms to Wavefront via proxy
   */
  DropwizardMetricsReporter dropwizardMetricsReporter = builder.build(wavefrontProxyClient);
```

### Option 2 - Report Dropwizard metrics and histograms to Wavefront via Direct Ingestion
```java
  /*
   * Using the 
   * 1) DropwizardMetricsReporter.Builder constructed above, and
   * 2) wavefrontDirectIngestionClient instantiated using above instructions
   * report metrics and histograms to Wavefront via direct ingestion
   */
  DropwizardMetricsReporter dropwizardMetricsReporter = builder.build(wavefrontDirectIngestionClient);
```

### Starting and stopping the reporter

```java
  /* Report metrics and histograms to Wavefront every 30 seconds */
  dropwizardMetricsReporter.start(30, TimeUnit.SECONDS);

  /* Get total failure count reported by this dropwizardMetricsReporter */
  int totalFailures = dropwizardMetricsReporter.getFailureCount();

  /* Before shutdown of your JVM app, don't forget to stop the reporter */
  dropwizardMetricsReporter.stop();
```

### Dropwizard entities that you can report to Wavefront
```java
  /* A raw counter that will be reported as it begins with prefix 'my*' */
  Counter counter = metricRegistry.counter("myCounter");
  
  /* 'notMyCounter' won't be reported as it does not begin with prefix - 'my*'  */
  Counter notReported = metricRegistry.counter("notMyCounter");
  
  /* Wavefront Delta Counter */
  DeltaCounter deltaCounter = DeltaCounter.get(metricRegistry, "myDeltaCounter");
  
  /* Gauge */
  AtomicInteger bufferSize = new AtomicInteger();
  Gauge gauge = metricRegistry.register("myGauge", () -> bufferSize.get());
    
  /* Meter */
  Meter meter = metricRegistry.meter("myMeter");
    
  /* Timer */
  Timer timer = metricRegistry.timer("myTimer");
  
  /* Default Dropwizard Histogram */
  Histogram dropwizardHistogram = metricRegistry.histogram("myDropwizardHistogram");
  
  /* WavefrontHistogram */
  WavefrontHistogram wavefrontHistogram = WavefrontHistogram.get(metricRegistry, "myWavefrontHistogram");
```

