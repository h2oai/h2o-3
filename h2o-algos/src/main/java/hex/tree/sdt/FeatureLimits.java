package hex.tree.sdt;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class FeatureLimits {
    public double min;
    public double max;

    public static int VALUES_COUNT_IN_RANGE = 100;
    
    public FeatureLimits(final double min, final double max) {
        this.min = min;
        this.max = max;
    }
    
    public FeatureLimits setNewMax(final double newMax) {
        max = newMax;
        return this;
    }
    
    public FeatureLimits setNewMin(final double newMin) {
        min = newMin;
        return this;
    }

    public Stream<Double> getFeatureRange() {
        double step = (max - min) / VALUES_COUNT_IN_RANGE;
        return DoubleStream.iterate(min + step, d -> d + step).limit(VALUES_COUNT_IN_RANGE - 1).boxed();
    }
    
    public static FeatureLimits clone(final FeatureLimits toClone) {
        return new FeatureLimits(toClone.min, toClone.max);
    }

}
