package com.codahale.metrics;

import com.wavefront.dropwizard.metrics.TaggedMetricName;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A {@link Timer} that is backed by a {@link WavefrontHistogram}, allowing data to be cleared
 * after it is flushed. This implementation also allows {@link TimeUnit} to be specified
 * for duration and/or for rate for each timer instance.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class WavefrontTimer extends Timer {
    private final Long durationFactor;
    private final Long rateFactor;
    private final Clock clock;
    private final Meter meter;
    private final WavefrontHistogram wfHistogram;

    private WavefrontTimer(TimeUnit durationUnit, TimeUnit rateUnit, Clock clock) {
        this.durationFactor = durationUnit == null ? null : durationUnit.toNanos(1);
        this.rateFactor = rateUnit == null ? null : rateUnit.toSeconds(1);
        this.clock = clock == null ? Clock.defaultClock() : clock;
        this.meter = new Meter(this.clock);
        this.wfHistogram = WavefrontHistogram.create(this.clock::getTime, true);
    }

    /**
     * Returns a new {@link Builder} for {@link WavefrontTimer}.
     *
     * @param registry  the registry to use.
     * @return a {@link Builder} instance for a {@link WavefrontTimer}.
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    public static class Builder {
        private final MetricRegistry registry;
        private TimeUnit durationUnit;
        private TimeUnit rateUnit;
        private Clock clock;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
        }

        /**
         * Specify a {@link TimeUnit} for the duration of this timer.
         *
         * @param durationUnit  a {@link TimeUnit} for the duration.
         * @return {@code this}
         */
        public Builder withDurationUnit(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Specify a {@link TimeUnit} for the rate of this timer.
         *
         * @param rateUnit  a {@link TimeUnit} for the rate.
         * @return {@code this}
         */
        public Builder withRateUnit(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Specify a {@link Clock} for this timer.
         *
         * @param clock  a {@link Clock} for this timer.
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Return the {@link WavefrontTimer} registered under this name; or build and register
         * a new {@link WavefrontTimer} with the given properties.
         *
         * @param taggedMetricName  the {@link TaggedMetricName} of the timer.
         * @return a new or pre-existing {@link WavefrontTimer}.
         * @throws IllegalStateException if a timer that is not a {@link WavefrontTimer} is already
         * registered under this name.
         */
        public WavefrontTimer build(TaggedMetricName taggedMetricName) {
            return build(taggedMetricName.encode());
        }

        /**
         * Return the {@link WavefrontTimer} registered under this name; or build and register
         * a new {@link WavefrontTimer} with the given properties.
         *
         * @param metricName  the name of the timer.
         * @return a new or pre-existing {@link WavefrontTimer}.
         * @throws IllegalStateException if a timer that is not a {@link WavefrontTimer} is already
         * registered under this name.
         */
        public WavefrontTimer build(String metricName) {
            WavefrontTimer timer = new WavefrontTimer(durationUnit, rateUnit, clock);
            try {
                return registry.register(metricName, timer);
            } catch(IllegalArgumentException e) {
                Timer existing = registry.timer(metricName);
                if (existing instanceof WavefrontTimer) {
                    return (WavefrontTimer) existing;
                } else {
                    throw new IllegalStateException("Existing metric of type: Timer found " +
                            "registered to metricName: " + metricName);
                }
            }
        }
    }

    @Override
    public void update(long duration, TimeUnit unit) {
        update(unit.toNanos(duration));
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        final long startTime = clock.getTick();
        try {
            return event.call();
        } finally {
            update(clock.getTick() - startTime);
        }
    }

    @Override
    public <T> T timeSupplier(Supplier<T> event) {
        final long startTime = clock.getTick();
        try {
            return event.get();
        } finally {
            update(clock.getTick() - startTime);
        }
    }

    @Override
    public void time(Runnable event) {
        final long startTime = clock.getTick();
        try {
            event.run();
        } finally {
            update(clock.getTick() - startTime);
        }
    }

    @Override
    public long getCount() {
        return wfHistogram.getCount();
    }

    @Override
    public double getFifteenMinuteRate() {
        return meter.getFifteenMinuteRate();
    }

    @Override
    public double getFiveMinuteRate() {
        return meter.getFiveMinuteRate();
    }

    @Override
    public double getMeanRate() {
        return meter.getMeanRate();
    }

    @Override
    public double getOneMinuteRate() {
        return meter.getOneMinuteRate();
    }

    @Override
    public WavefrontSnapshot getSnapshot() {
        return wfHistogram.getSnapshot();
    }

    /**
     * Returns a snapshot of the histogram distribution and clears all data in the snapshot,
     * preventing data from being flushed more than once.
     *
     * @return a {@link WavefrontSnapshot}.
     */
    public WavefrontSnapshot flushSnapshot() {
        return wfHistogram.flushSnapshot();
    }

    /**
     * Converts the duration value to duration in nanoseconds if a particular duration TimeUnit is
     * specified for this timer.  Otherwise, returns a default value.
     *
     * @param duration      The value to convert.
     * @param defaultValue  The value to return if no duration unit is specified for this timer.
     * @return the value in nanoseconds.
     */
    public double convertDuration(double duration, double defaultValue) {
        return durationFactor == null ? defaultValue : duration / durationFactor;
    }

    /**
     * Converts the rate value to a per-second rate value if a particular rate TimeUnit is
     * specified for this timer.  Otherwise, returns a default value.
     *
     * @param rate          The value to convert.
     * @param defaultValue  The value to return if no rate unit is specified for this timer.
     * @return the per-second value.
     */
    public double convertRate(double rate, double defaultValue) {
        return rateFactor == null ? defaultValue : rate * rateFactor;
    }

    private void update(long duration) {
        if (duration >= 0) {
            wfHistogram.update(duration);
            meter.mark();
        }
    }
}
