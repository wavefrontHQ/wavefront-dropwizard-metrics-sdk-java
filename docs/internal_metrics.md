# Internal Diagnostic Metrics

This SDK automatically collects a set of diagnostic metrics that allow you to monitor your `DropwizardMetricsReporter` instance. These metrics are collected once per minute and are reported to Wavefront using your `WavefrontSender` instance.

The following is a list of the diagnostic metrics that are collected:

|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.dropwizard_metrics.reporter.gauges.reported.count                |Counter    |Times that gauges are reported|
|~sdk.java.dropwizard_metrics.reporter.delta_counters.reported.count        |Counter    |Times that delta counters are reported|
|~sdk.java.dropwizard_metrics.reporter.counters.reported.count              |Counter    |Times that non-delta counters are reported|
|~sdk.java.dropwizard_metrics.reporter.wavefront_histograms.reported.count  |Counter    |Times that Wavefront histograms are reported|
|~sdk.java.dropwizard_metrics.reporter.histograms.reported.count            |Counter    |Times that non-Wavefront histograms are reported|
|~sdk.java.dropwizard_metrics.reporter.meters.reported.count                |Counter    |Times that meters are reported|
|~sdk.java.dropwizard_metrics.reporter.timers.reported.count                |Counter    |Times that timers are reported|
|~sdk.java.dropwizard_metrics.reporter.errors.count                         |Counter    |Exceptions encountered while reporting|

Each of the above metrics is reported with the same source and application tags that are specified for your `DropwizardMetricsReporter`.

For information regarding diagnostic metrics for your `WavefrontSender` instance, [see here](https://github.com/wavefrontHQ/wavefront-sdk-java/tree/master/docs/internal_metrics.md).