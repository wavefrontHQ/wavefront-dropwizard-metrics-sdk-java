package com.codahale.metrics;

import com.wavefront.sdk.common.Constants;

/**
 * Wavefront delta counter which has the ability to report delta values aggregated on Wavefront
 * server side. Value is reset in the reporter every time the value is reported.
 * Caveat: Cannot use the same DeltaCounter registry for multiple reporters as the reporter will
 * change the state of the DeltaCounter every time the value is reported.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class DeltaCounter extends Counter {

  private DeltaCounter() {
  }

  public static DeltaCounter get(MetricRegistry registry, String metricName) {
    if (registry == null || metricName == null || metricName.isEmpty()) {
      throw new IllegalArgumentException("Invalid arguments");
    }

    if (!metricName.startsWith(Constants.DELTA_PREFIX) &&
        !metricName.startsWith(Constants.DELTA_PREFIX_2)) {
      metricName = Constants.DELTA_PREFIX + metricName;
    }
    DeltaCounter counter = new DeltaCounter();
    try {
      Metric metric = registry.getMetrics().get(metricName);
      if (metric instanceof DeltaCounter) {
        return (DeltaCounter) metric;
      }
      return registry.register(metricName, counter);
    } catch(IllegalArgumentException e) {
      Counter existing = registry.counter(metricName);
      if (existing instanceof DeltaCounter) {
        return (DeltaCounter) existing;
      } else {
        throw new IllegalStateException("Existing metric of type: Counter found registered to " +
            "metricName: " + metricName);
      }
    }
  }
}
