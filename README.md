# Wavefront Dropwizard Metrics Reporter [![build status][ci-img]][ci] [![Released Version][maven-img]][maven]

This library supports reporting [Dropwizard metrics and histograms](https://metrics.dropwizard.io) to Wavefront.

## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-dropwizard-metrics-sdk-java</artifactId>
    <version>$releaseVersion</version>
</dependency>
```
Replace `$releaseVersion` with the latest version available on [maven](http://search.maven.org/#search%7Cga%7C1%7Cwavefront-dropwizard-metrics-sdk-java).

## DropwizardMetricsReporter

This SDK provides a `DropwizardMetricsReporter` for reporting metrics and histograms to Wavefront.

To create a `DropwizardMetricsReporter`:
1. Create a `DropwizardMetricsReporter.Builder` instance
2. Create a `WavefrontSender`: a low-level interface that handles sending data to Wavefront
3. Finally create a `DropwizardMetricsReporter` using the builder

The sections below detail each of the above steps.

### 1. Create DropwizardMetricsReporter.Builder
```java
MetricRegistry metricRegistry = new MetricRegistry();

// Create a builder instance
DropwizardMetricsReporter.Builder builder = DropwizardMetricsReporter.forRegistry(metricRegistry);

// Set a relevant source for your metrics and histograms
builder.withSource("mySource");

// Set reporter level point tags for your metrics and histograms
// These point tags are sent with every metric and histogram reported to Wavefront
builder.withReporterPointTags(ImmutableMap.<String, String>builder().
        put("env", "Staging").
        put("location", "SF").build());

// Add specific reporter level point tag key-value for your metrics and histograms
builder.withReporterPointTag("cluster", "us-west");

/**
 * Optional: You can choose to add ApplicationTags which will be propagated
 * as first class tags along with the reported metric.
 * You encapsulate application tags in an `ApplicationTags` object.
 * See https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md for details.
 */
builder.withApplicationTags(new ApplicationTags.Builder("OrderingApp", "Inventory").
        cluster("us-west-1").shard("primary").
        customTags(new HashMap<String, String>(){{
          put("env", "Staging");
          put("location", "SF"); }}).build());

// Optional: Invoke this method to report your metrics and histograms with the given prefix
builder.prefixedWith("myPrefix");

// Optional: Invoke this method if you want to report JVM metrics for your Java app
builder.withJvmMetrics();

// Optional: Invoke this method if you want to report minute bin Wavefront histograms
builder.reportMinuteDistribution();

// Optional: Invoke this method if you want to report hour bin Wavefront histograms
builder.reportHourDistribution();

// Optional: Invoke this method if you want to report day bin Wavefront histograms
builder.reportDayDistribution();
```
Remember to replace the source and prefix with relevant values. See the advanced section below for more builder options.

#### Advanced Builder Options
```java
// Explicitly set the clock if you wish to override default behavior
builder.withClock(new Clock() {
  @Override
  public long getTick() {
    return System.currentTimeMillis();
  }
});

// Set a filter to only report metrics that begin with 'my*'
builder.filter(MetricFilter.startsWith("my"));

// Don't report stddev and m15
builder.disabledMetricAttributes(ImmutableSet.<MetricAttribute>builder().
    add(MetricAttribute.STDDEV).
    add(MetricAttribute.M15_RATE).
    build());
```

### 2. Set up a WavefrontSender
You can choose to send data to Wavefront using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html). See the [Wavefront sender documentation](https://github.com/wavefrontHQ/wavefront-sdk-java#set-up-a-wavefrontsender) for details on instantiating a proxy or direct ingestion client.

### 3. Create a DropwizardMetricsReporter
Once you have a Wavefront sender, create the `DropwizardMetricsReporter`:

```java
// Using the DropwizardMetricsReporter.Builder and wavefrontSender constructed above
// Create a DropwizardMetricsReporter instance
DropwizardMetricsReporter dropwizardMetricsReporter = builder.build(wavefrontSender);

// Start the reporter to report metrics/histograms at regular interval (ex: 30s)
dropwizardMetricsReporter.start(30, TimeUnit.SECONDS);
```

#### Stopping the reporter
Remember to stop the reporter before shutting down your application:
```java
// Get total failure count reported by this reporter
int totalFailures = dropwizardMetricsReporter.getFailureCount();

// stop the reporter
dropwizardMetricsReporter.stop();
```

## Dropwizard entities that you can report to Wavefront
The [Dropwizard metrics](https://metrics.dropwizard.io) library supports various [metric types](https://metrics.dropwizard.io/4.0.0/manual/core.html). This SDK additionally provides a
[`DeltaCounter`](https://docs.wavefront.com/delta_counters.html) and a [`WavefrontHistogram`](https://docs.wavefront.com/proxies_histograms.html).

Once you have created/started the reporter, metrics/histograms you create are automatically reported to Wavefront:

```java
// A raw counter that will be reported as it begins with prefix 'my*'
Counter counter = metricRegistry.counter("myCounter");

// 'notMyCounter' won't be reported as it does not begin with prefix - 'my*'
Counter notReported = metricRegistry.counter("notMyCounter");

// Wavefront Delta Counter
DeltaCounter deltaCounter = DeltaCounter.get(metricRegistry, "myDeltaCounter");

// Gauge
AtomicInteger bufferSize = new AtomicInteger();
Gauge gauge = metricRegistry.register("myGauge", () -> bufferSize.get());

// Meter
Meter meter = metricRegistry.meter("myMeter");

// Timer
Timer timer = metricRegistry.timer("myTimer");

// Default Dropwizard Histogram
Histogram dropwizardHistogram = metricRegistry.histogram("myDropwizardHistogram");

// WavefrontHistogram
WavefrontHistogram wavefrontHistogram = WavefrontHistogram.get(metricRegistry, "myWavefrontHistogram");
```

[ci-img]: https://travis-ci.com/wavefrontHQ/wavefront-dropwizard-metrics-sdk-java.svg?branch=master
[ci]: https://travis-ci.com/wavefrontHQ/wavefront-dropwizard-metrics-sdk-java
[maven-img]: https://img.shields.io/maven-central/v/com.wavefront/wavefront-dropwizard-metrics-sdk-java.svg?maxAge=2592000
[maven]: http://search.maven.org/#search%7Cga%7C1%7Cwavefront-dropwizard-metrics-sdk-java
