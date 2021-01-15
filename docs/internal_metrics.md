# Internal Diagnostic Metrics

This SDK automatically collects a set of diagnostic metrics that allow you to monitor your `DropwizardMetricsReporter` instance. These metrics are collected once per minute and are reported to Wavefront using your `WavefrontSender` instance.

The following is a list of the diagnostic metrics that are collected:

|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.dropwizard_metrics.reporter.gauges.reported.count                |Delta Counter    |The number of gauges reported.|
|~sdk.java.dropwizard_metrics.reporter.delta_counters.reported.count        |Delta Counter    |The number of delta counters reported.|
|~sdk.java.dropwizard_metrics.reporter.counters.reported.count              |Delta Counter    |The number of non-delta counters reported.|
|~sdk.java.dropwizard_metrics.reporter.wavefront_histograms.reported.count  |Delta Counter    |The number of Wavefront histograms reported.|
|~sdk.java.dropwizard_metrics.reporter.histograms.reported.count            |Delta Counter    |The number of non-Wavefront histograms reported.|
|~sdk.java.dropwizard_metrics.reporter.meters.reported.count                |Delta Counter    |The number of meters reported.|
|~sdk.java.dropwizard_metrics.reporter.timers.reported.count                |Delta Counter    |The number of timers reported.|
|~sdk.java.dropwizard_metrics.reporter.errors.count                         |Delta Counter    |The number of exceptions encountered while reporting.|

The same source and application tags used in the `DropwizardMetricsReporter` are used to report the metrics shown above.

For details on diagnostic metrics for your `WavefrontSender` instance, see [Internal Diagnostic Metrics for Java SDKs](https://github.com/wavefrontHQ/wavefront-sdk-doc-sources/blob/master/java/internalmetrics.md#internal-diagnostic-metrics).
