# Wavefront Dropwizard Metrics SDK [![build status][ci-img]][ci] [![Released Version][maven-img]][maven]

The Wavefront by VMware Dropwizard Metrics SDK for Java is a library that supports reporting [Dropwizard metrics and histograms](https://metrics.dropwizard.io) to Wavefront.

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

## Set Up a DropwizardMetricsReporter

This SDK provides a `DropwizardMetricsReporter` for reporting metrics and histograms to Wavefront.

The steps for creating a `DropwizardMetricsReporter` are:
1. Create a `DropwizardMetricsReporter.Builder` instance.
2. Optionally use the builder to configure the `DropwizardMetricsReporter`.
3. Create a `WavefrontSender` for sending data to Wavefront.
4. Use the builder to create a `DropwizardMetricsReporter` with the `WavefrontSender`.

For the details of each step, see the sections below.

### 1. Create a Builder for a DropwizardMetricsReporter

A `DropwizardMetricsReporter` object reports any metrics and histograms you register in a `MetricRegistry`. This step creates a builder that supports configuring the metrics reporter.

```java
// Create a registry
MetricRegistry metricRegistry = new MetricRegistry();

// Create a builder instance for the registry
DropwizardMetricsReporter.Builder builder = DropwizardMetricsReporter.forRegistry(metricRegistry);
```

### 2. Configure the DropwizardMetricsReporter

You can use the `DropwizardMetricsReporter` builder to specify various optional properties.

#### Basic Properties

```java
// Optional: Set a nondefault source for your metrics and histograms.
// Defaults to hostname if omitted
builder.withSource("mySource");

// Add individual reporter-level point tags for your metrics and histograms
// The point tags are sent with every metric and histogram reported to Wavefront.
builder.withReporterPointTag("env", "staging");  // Example - replace values!
builder.withReporterPointTag("location", "SF");  // Example - replace values!

// Optional: Add application tags, which are propagated as point tags with the reported metric.
// See https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md for details.
builder.withApplicationTags(new ApplicationTags.Builder("OrderingApp", "Inventory").
       cluster("us-west-1").
       shard("primary").build());   // Example - replace values!

// Optional: Report your metrics and histograms with the specified prefix.
builder.prefixedWith("myPrefix");   // Example - replace value!

// Optional: Report JVM metrics for your Java application.
builder.withJvmMetrics();

// Optional: Report minute bin Wavefront histograms.
builder.reportMinuteDistribution();

// Optional: Report hour bin Wavefront histograms.
builder.reportHourDistribution();

// Optional: Report day bin Wavefront histograms.
builder.reportDayDistribution();
```

#### Advanced Properties
```java
// Optional: Explicitly set the clock to override default behavior
builder.withClock(new Clock() {
  @Override
  public long getTick() {
    return System.currentTimeMillis();
  }
});

// Optional: Set a filter to report metrics only if they begin with 'my*'
builder.filter(MetricFilter.startsWith("my"));

// Optional: Don't report stddev and m15
builder.disabledMetricAttributes(ImmutableSet.<MetricAttribute>builder().
    add(MetricAttribute.STDDEV).
    add(MetricAttribute.M15_RATE).
    build());
```

### 3. Set Up a WavefrontSender

A `WavefrontSender` object implements the low-level interface for sending data to Wavefront. You can choose to send data using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

See [Set Up a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#set-up-a-wavefrontsender) for details on instantiating a proxy or direct ingestion client.

**Note:** If you are using multiple Wavefront Java SDKs, see [Sharing a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md) for information about sharing a single `WavefrontSender` instance across SDKs.


### 4. Create a DropwizardMetricsReporter
Use the configured builder to create the `DropwizardMetricsReporter`. You must specify the `WavefrontSender` object (see above).

```java
// Create a DropwizardMetricsReporter instance
WavefrontSender wavefrontSender = buildWavefrontSender(); // pseudocode
DropwizardMetricsReporter dropwizardMetricsReporter = builder.build(wavefrontSender);
```
## Start the DropwizardMetricsReporter

You start the `DropwizardMetricsReporter` explicitly to start reporting any metrics or histograms you create. Reporting continues until you stop the `DropwizardMetricsReporter` (see below).

The `DropwizardMetricsReporter` reports metrics/histograms at regular intervals. You specify the length of the reporting interval to control how often data is forwarded to the `WavefrontSender`. The reporting interval determines the timestamps on the data sent to Wavefront.

```java
// Start the reporter to report metrics/histograms at regular interval (example: 30s)
dropwizardMetricsReporter.start(30, TimeUnit.SECONDS);
```

## Stop the DropwizardMetricsReporter
You must explicitly stop the `DropwizardMetricsReporter` before shutting down your application.

```java
// Get total failure count reported by this reporter
int totalFailures = dropwizardMetricsReporter.getFailureCount();

// stop the reporter
dropwizardMetricsReporter.stop();
```

## Types of Data You Can Report to Wavefront
The Dropwizard metrics library supports various [metric types](https://metrics.dropwizard.io/4.0.0/manual/core.html). This Wavefront SDK additionally provides a
[`DeltaCounter`](https://docs.wavefront.com/delta_counters.html) type and a [`WavefrontHistogram`](https://docs.wavefront.com/proxies_histograms.html) type.

After you have created and started the `DropwizardMetricsReporter`, the metrics/histograms you create are automatically reported to Wavefront.

```java
// Assume a DropwizardMetricsReporter that is configured with the filter shown in Advanced Properties, above.

// Raw Counters
// Counter name begins with 'my*', so it's reported.
Counter counter = metricRegistry.counter("myCounter");
// Counter name does not begin with 'my*', so it's ignored.
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
