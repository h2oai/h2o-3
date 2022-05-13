package hex.tree.sdt;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataFeaturesLimits {
    private List<FeatureLimits> featuresLimits;
    
    public DataFeaturesLimits(final List<FeatureLimits> featureLimits) {
        this.featuresLimits = featureLimits;
    }
    public DataFeaturesLimits clone() {
        return new DataFeaturesLimits(featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
    }
    public DataFeaturesLimits updateMin(final int selectedFeature, final double newMin) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone.featuresLimits.get(selectedFeature).setNewMin(newMin);
        return clone;
    }
    
    public DataFeaturesLimits updateMax(final int selectedFeature, final double newMax) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone.featuresLimits.get(selectedFeature).setNewMax(newMax);
        return clone;
    }

    public Stream<Double> getFeatureRange(final int featureIndex) {
        return featuresLimits.get(featureIndex).getFeatureRange();
    }
    
}
