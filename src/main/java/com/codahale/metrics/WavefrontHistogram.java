package com.codahale.metrics;

import com.wavefront.dropwizard.metrics.TaggedMetricName;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * WavefrontHistogram implementation for com.codahale.metrics Caveat: Cannot use the same
 * WavefrontHistogram registry for multiple reporters as the reporter will change the state of the
 * WavefrontHistogram every time the value is reported.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontHistogram extends Histogram implements Metric {

  private final WavefrontHistogramImpl delegate;
  private final boolean reportSnapshot;

  private WavefrontHistogram(TDigestReservoir reservoir,
                             Supplier<Long> clockMillis,
                             boolean reportSnapshot) {
    super(reservoir);
    delegate = new WavefrontHistogramImpl(clockMillis == null ?
            System::currentTimeMillis : clockMillis);
    this.reportSnapshot = reportSnapshot;
  }

  public static WavefrontHistogram get(MetricRegistry registry, String metricName) {
    return forRegistry(registry).build(metricName);
  }

  public static WavefrontHistogram get(MetricRegistry registry,
                                       String metricName,
                                       Supplier<Long> clock) {
    return forRegistry(registry).withClock(clock).build(metricName);
  }

  /**
   * Returns a new {@link Builder} for {@link WavefrontHistogram}.
   *
   * @param registry  the registry to use.
   * @return a {@link Builder} instance for a {@link WavefrontHistogram}.
   */
  public static Builder forRegistry(MetricRegistry registry) {
    return new Builder(registry);
  }

  public static class Builder {
    private final MetricRegistry registry;
    private Supplier<Long> clock;
    private boolean reportSnapshot;

    private Builder(MetricRegistry registry) {
      this.registry = registry;
    }

    /**
     * Specify a clock for this histogram.
     *
     * @param clock  a supplier of timestamps in milliseconds.
     * @return {@code this}
     */
    public Builder withClock(Supplier<Long> clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Report snapshots of the histogram instead of the histogram distributions themselves.
     *
     * @return {@code this}
     */
    public Builder reportSnapshot() {
      this.reportSnapshot = true;
      return this;
    }

    /**
     * Return the {@link WavefrontHistogram} registered under this name; or build and register
     * a new {@link WavefrontHistogram} with the given properties.
     *
     * @param taggedMetricName  the {@link TaggedMetricName} of the histogram.
     * @return a new or pre-existing {@link WavefrontHistogram}.
     * @throws IllegalStateException if a timer that is not a {@link WavefrontHistogram} is already
     * registered under this name.
     */
    public WavefrontHistogram build(TaggedMetricName taggedMetricName) {
      return build(taggedMetricName.encode());
    }

    /**
     * Return the {@link WavefrontHistogram} registered under this name; or build and register
     * a new {@link WavefrontHistogram} with the given properties.
     *
     * @param metricName  the name of the histogram.
     * @return a new or pre-existing {@link WavefrontHistogram}.
     * @throws IllegalStateException if a timer that is not a {@link WavefrontHistogram} is already
     * registered under this name.
     */
    public WavefrontHistogram build(String metricName) {
      WavefrontHistogram tDigestHistogram = create(clock, reportSnapshot);
      try {
        return registry.register(metricName, tDigestHistogram);
      } catch (IllegalArgumentException e) {
        Histogram existing = registry.histogram(metricName);
        if (existing instanceof WavefrontHistogram) {
          return (WavefrontHistogram) existing;
        } else {
          throw new IllegalStateException("Existing metric of type: Histogram found registered " +
                  "to metricName: " + metricName);
        }
      }
    }
  }

  /**
   * Instantiates and returns a new {@link WavefrontHistogram}.
   *
   * @param clock           the clock for this histogram.
   * @param reportSnapshot  true to report snapshots for this histogram,
   *                        false to report histogram distributions.
   * @return a new {@link WavefrontHistogram}.
   */
  static WavefrontHistogram create(Supplier<Long> clock, boolean reportSnapshot) {
    // Awkward construction trying to fit in with Dropwizard Histogram
    TDigestReservoir reservoir = new TDigestReservoir();
    WavefrontHistogram tDigestHistogram =
            new WavefrontHistogram(reservoir, clock, reportSnapshot);
    reservoir.set(tDigestHistogram);
    return tDigestHistogram;
  }

  @Override
  public void update(int value) {
    delegate.update(value);
  }

  @Override
  public void update(long value) {
    delegate.update(value);
  }

  public void update(double value) {
    delegate.update(value);
  }

  public void bulkUpdate(List<Double> means, List<Integer> counts) {
    delegate.bulkUpdate(means, counts);
  }

  @Override
  public long getCount() {
    return delegate.getCount();
  }

  @Override
  public WavefrontSnapshot getSnapshot() {
    return getWavefrontSnapshot(delegate.getSnapshot());
  }

  /**
   * Returns a snapshot of the histogram distribution and clears all data in the snapshot,
   * preventing data from being flushed more than once.
   *
   * @return a {@link WavefrontSnapshot}.
   */
  public WavefrontSnapshot flushSnapshot() {
    return getWavefrontSnapshot(delegate.flushSnapshot());
  }

  private WavefrontSnapshot getWavefrontSnapshot(WavefrontHistogramImpl.Snapshot delegateSnapshot) {
    return new WavefrontSnapshot() {
      @Override
      public double getMedian() {
        return getValue(.50);
      }

      @Override
      public double get75thPercentile() {
        return getValue(.75);
      }

      @Override
      public double get95thPercentile() {
        return getValue(.95);
      }

      @Override
      public double get98thPercentile() {
        return getValue(.98);
      }

      @Override
      public double get99thPercentile() {
        return getValue(.99);
      }

      @Override
      public double get999thPercentile() {
        return getValue(.999);
      }

      @Override
      public long getMax() {
        return Math.round(delegateSnapshot.getMax());
      }

      @Override
      public double getMean() {
        return convert(delegateSnapshot.getMean());
      }

      @Override
      public long getMin() {
        return (long) delegateSnapshot.getMin();
      }

      @Override
      public double getStdDev() {
        return delegate.stdDev();
      }

      @Override
      public void dump(OutputStream outputStream) {
      }

      @Override
      public double getValue(double quantile) {
        return convert(delegateSnapshot.getValue(quantile));
      }

      @Override
      public long[] getValues() {
        return new long[0];
      }

      @Override
      public int size() {
        return delegateSnapshot.getSize();
      }

      @Override
      public double getSum() {
          return delegateSnapshot.getSum();
      }

      private double convert(double value) {
        return Double.isNaN(value) ? 0 : value;
      }
    };
  }

  /**
   * Returns a list of the histogram's distributions and clears all data in the distributions,
   * preventing data from being flushed more than once.
   *
   * @return a list of histogram distributions.
   */
  public List<WavefrontHistogramImpl.Distribution> flushDistributions() {
    return delegate.flushDistributions();
  }

  /**
   * @return true if the histogram's snapshots are reported, false if the histogram's
   * distributions are reported.
   */
  public boolean isReportingSnapshot() {
    return reportSnapshot;
  }

  private static class TDigestReservoir implements Reservoir {

    private WavefrontHistogram wfHist;

    void set(WavefrontHistogram tdm) {
      this.wfHist = tdm;
    }

    @Override
    public int size() {
      return (int) wfHist.getCount();
    }

    @Override
    public void update(long l) {
      wfHist.update(l);
    }

    @Override
    public Snapshot getSnapshot() {
      return wfHist.getSnapshot();
    }
  }
}
