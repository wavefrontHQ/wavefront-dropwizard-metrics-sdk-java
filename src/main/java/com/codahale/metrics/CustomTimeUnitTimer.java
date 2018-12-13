package com.codahale.metrics;

import com.wavefront.dropwizard.metrics.TaggedMetricName;

import java.util.concurrent.TimeUnit;

/**
 * A {@link Timer} that has its own {@link TimeUnit} for duration and for rate.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class CustomTimeUnitTimer extends Timer {
    private final long durationFactor;
    private final long rateFactor;

    private CustomTimeUnitTimer(TimeUnit durationUnit, TimeUnit rateUnit) {
        this.durationFactor = durationUnit.toNanos(1);
        this.rateFactor = rateUnit.toSeconds(1);
    }

    public static CustomTimeUnitTimer get(MetricRegistry registry,
                                          TaggedMetricName taggedMetricName,
                                          TimeUnit durationUnit, TimeUnit rateUnit) {
        return get(registry, taggedMetricName.encode(), durationUnit, rateUnit);
    }

    public static CustomTimeUnitTimer get(MetricRegistry registry, String metricName,
                                          TimeUnit durationUnit, TimeUnit rateUnit) {
        CustomTimeUnitTimer timer = new CustomTimeUnitTimer(durationUnit, rateUnit);
        try {
            return registry.register(metricName, timer);
        } catch(IllegalArgumentException e) {
            Timer existing = registry.timer(metricName);
            if (existing instanceof CustomTimeUnitTimer) {
                return (CustomTimeUnitTimer) existing;
            } else {
                throw new IllegalStateException("Existing metric of type: Timer found registered " +
                        "to metricName: " + metricName);
            }
        }
    }

    public double convertDuration(double duration) {
        return duration / durationFactor;
    }

    public double convertRate(double rate) {
        return rate * rateFactor;
    }
}
