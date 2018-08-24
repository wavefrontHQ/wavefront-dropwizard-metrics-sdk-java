package com.codahale.metrics;

import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * WavefrontHistogram implementation for com.codahale.metrics
 * Caveat: Cannot use the same WavefrontHistogram registry for multiple reporters as the reporter
 * will change the state of the WavefrontHistogram every time the value is reported.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontHistogram extends Histogram implements Metric {

  private final WavefrontHistogramImpl delegate;

  public static WavefrontHistogram get(MetricRegistry registry, String metricName) {
    return get(registry, metricName, System::currentTimeMillis);
  }

  public static synchronized WavefrontHistogram get(MetricRegistry registry,
                                                    String metricName,
                                                    Supplier<Long> clock) {
    // Awkward construction trying to fit in with Dropwizard Histogram
    TDigestReservoir reservoir = new TDigestReservoir();
    WavefrontHistogram tDigestHistogram = new WavefrontHistogram(reservoir, clock);
    reservoir.set(tDigestHistogram);
    try {
      return registry.register(metricName, tDigestHistogram);
    } catch(IllegalArgumentException e) {
      Histogram existing = registry.histogram(metricName);
      if (existing instanceof WavefrontHistogram) {
        return (WavefrontHistogram) existing;
      } else {
        throw new IllegalStateException("Found non-WavefrontHistogram: " + existing);
      }
    }
  }

  private WavefrontHistogram(TDigestReservoir reservoir, Supplier<Long> clockMillis) {
    super(reservoir);
    delegate = new WavefrontHistogramImpl(clockMillis);
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

  @Override
  public long getCount() {
    return delegate.getCount();
  }

  @Override
  public Snapshot getSnapshot() {
    final WavefrontHistogramImpl.Snapshot delegateSnapshot =
        delegate.getSnapshot();

    return new Snapshot() {
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
        return delegateSnapshot.getMean();
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
        return delegateSnapshot.getValue(quantile);
      }

      @Override
      public long[] getValues() {
        return new long[0];
      }

      @Override
      public int size() {
        return delegateSnapshot.getSize();
      }
    };
  }

  public List<WavefrontHistogramImpl.Distribution> flushDistributions() {
    return delegate.flushDistributions();
  }

  private static class TDigestReservoir implements Reservoir {

    private WavefrontHistogram wfHist;

    void set(WavefrontHistogram tdm) {
      this.wfHist = tdm;
    }

    @Override
    public int size() { return (int) wfHist.getCount(); }

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
