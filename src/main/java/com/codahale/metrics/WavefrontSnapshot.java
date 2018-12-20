package com.codahale.metrics;

/**
 * Extension of {@link Snapshot} that is used by {@link WavefrontHistogram} and
 * {@link WavefrontTimer}.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public abstract class WavefrontSnapshot extends Snapshot {

    /**
     * Returns the sum of all values in the snapshot.
     *
     * @return the sum of all values.
     */
    public abstract double getSum();

}
