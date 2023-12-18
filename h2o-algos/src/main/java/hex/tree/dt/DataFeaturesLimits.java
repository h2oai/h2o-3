package hex.tree.dt;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Features limits for the whole dataset.
 */
public class DataFeaturesLimits {
    // limits for each feature
    private final List<FeatureLimits> _featuresLimits;

    public DataFeaturesLimits(final List<FeatureLimits> featureLimits) {
        this._featuresLimits = featureLimits;
    }

    public DataFeaturesLimits(final double[][] featureLimits) {
        this._featuresLimits = Arrays.stream(featureLimits)
                .map(dd -> new FeatureLimits(dd[0], dd[1]))
                .collect(Collectors.toList());
    }

    public DataFeaturesLimits clone() {
        return new DataFeaturesLimits(_featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
    }

    /**
     * Creates new instance of limits with updated min.
     *
     * @param selectedFeature feature index to update min
     * @param newMin          new min value for feature
     * @return clone with updated min
     */
    public DataFeaturesLimits updateMin(final int selectedFeature, final double newMin) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone._featuresLimits.get(selectedFeature).setNewMin(newMin);
        return clone;
    }

    /**
     * Creates new instance of limits with updated max.
     *
     * @param selectedFeature feature index to update max
     * @param newMax          new max value for feature
     * @return clone with updated max
     */
    public DataFeaturesLimits updateMax(final int selectedFeature, final double newMax) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(FeatureLimits::clone).collect(Collectors.toList()));
        clone._featuresLimits.get(selectedFeature).setNewMax(newMax);
        return clone;
    }

    public FeatureLimits getFeatureLimits(int featureIndex) {
        return _featuresLimits.get(featureIndex);
    }

    /**
     * Serialize limits do n x 2 array, so it can be passed to MR task
     *
     * @return
     */
    public double[][] toDoubles() {
        return _featuresLimits.stream().map(v -> new double[]{v._min, v._max}).toArray(double[][]::new);
    }

    /**
     * Get count of features.
     * @return count of features
     */
    public int featuresCount() {
        return _featuresLimits.size();
    }

    public boolean equals(DataFeaturesLimits other) {
        if (this == other) {
            return true;
        }
        if (other == null || other.featuresCount() != featuresCount()) {
            return false;
        }
        
        for (int i = 0; i < _featuresLimits.size(); i++) {
            if (!_featuresLimits.get(i).equals(other._featuresLimits.get(i))) {
                return false;
            }
        }
        return true;
    }


}
