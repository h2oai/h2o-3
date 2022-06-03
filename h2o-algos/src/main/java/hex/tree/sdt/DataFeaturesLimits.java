package hex.tree.sdt;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataFeaturesLimits {
    private final List<FeatureLimits> _featuresLimits;

    public DataFeaturesLimits(final List<FeatureLimits> featureLimits) {
        this._featuresLimits = featureLimits;
    }
    public DataFeaturesLimits clone() {
        return new DataFeaturesLimits(_featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
    }

    public DataFeaturesLimits updateMin(final int selectedFeature, final double newMin) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone._featuresLimits.get(selectedFeature).setNewMin(newMin);
        return clone;
    }

    public DataFeaturesLimits updateMax(final int selectedFeature, final double newMax) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone._featuresLimits.get(selectedFeature).setNewMax(newMax);
        return clone;
    }

    public Stream<Double> getFeatureRange(final int featureIndex) {
        return _featuresLimits.get(featureIndex).getFeatureRange();
    }
    
}
