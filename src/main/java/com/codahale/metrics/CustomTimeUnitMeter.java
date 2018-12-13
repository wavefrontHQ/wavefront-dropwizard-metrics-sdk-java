package com.codahale.metrics;

import com.wavefront.dropwizard.metrics.TaggedMetricName;

import java.util.concurrent.TimeUnit;

/**
 * A {@link Meter} thta has its own {@link TimeUnit} for rate.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class CustomTimeUnitMeter extends Meter {
    private final long rateFactor;

    private CustomTimeUnitMeter(TimeUnit rateUnit) {
        this.rateFactor = rateUnit.toSeconds(1);
    }

    public static CustomTimeUnitMeter get(MetricRegistry registry,
                                          TaggedMetricName taggedMetricName, TimeUnit rateUnit) {
        return get(registry, taggedMetricName.encode(), rateUnit);
    }

    public static CustomTimeUnitMeter get(MetricRegistry registry, String metricName,
                                          TimeUnit rateUnit) {
        CustomTimeUnitMeter meter = new CustomTimeUnitMeter(rateUnit);
        try {
            return registry.register(metricName, meter);
        } catch(IllegalArgumentException e) {
            Meter existing = registry.meter(metricName);
            if (existing instanceof CustomTimeUnitMeter) {
                return (CustomTimeUnitMeter) existing;
            } else {
                throw new IllegalStateException("Existing metric of type: Meter found registered " +
                        "to metricName: " + metricName);
            }
        }
    }

    public double convertRate(double rate) {
        return rate * rateFactor;
    }
}
