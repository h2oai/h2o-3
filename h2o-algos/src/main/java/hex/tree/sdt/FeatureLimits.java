package hex.tree.sdt;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class FeatureLimits {
    public double _min;
    public double _max;
    public double _step;

    public static int VALUES_COUNT_IN_RANGE = 100;

    public FeatureLimits(final double min, final double max) {
        _min = min;
        _max = max;
        _step = (_max - _min) / VALUES_COUNT_IN_RANGE;
    }

    public FeatureLimits setNewMax(final double newMax) {
        _max = newMax;
        return this;
    }

    public FeatureLimits setNewMin(final double newMin) {
        _min = newMin;
        return this;
    }


    // todo - remove this from this class - not his responsibility (now used for not-binning strategy)
    public Stream<Double> getFeatureRange() {
        return DoubleStream.iterate(_min + _step, d -> d + _step).limit(VALUES_COUNT_IN_RANGE - 1).boxed();
    }

    public static FeatureLimits clone(final FeatureLimits toClone) {
        return new FeatureLimits(toClone._min, toClone._max);
    }

}
