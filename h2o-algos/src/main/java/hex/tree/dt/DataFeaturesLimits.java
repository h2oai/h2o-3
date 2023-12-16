package hex.tree.dt;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static hex.tree.dt.NumericFeatureLimits.*;

/**
 * Features limits for the whole dataset.
 */
public class DataFeaturesLimits {
    // limits for each feature
    private final List<AbstractFeatureLimits> _featuresLimits;

    public DataFeaturesLimits(final List<AbstractFeatureLimits> featureLimits) {
        this._featuresLimits = featureLimits;
    }

    public DataFeaturesLimits(final double[][] featureLimits) {
        this._featuresLimits = Arrays.stream(featureLimits)
                .map(dd -> dd[NUMERICAL_FLAG] == -1.0 
                        ? new NumericFeatureLimits(dd[LIMIT_MIN], dd[LIMIT_MAX]) 
                        : new CategoricalFeatureLimits(dd))
                .collect(Collectors.toList());
    }

    public DataFeaturesLimits clone() {
        return new DataFeaturesLimits(_featuresLimits.stream().map(AbstractFeatureLimits::clone).collect(Collectors.toList()));
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
                _featuresLimits.stream().map(AbstractFeatureLimits::clone).collect(Collectors.toList()));
        ((NumericFeatureLimits) clone._featuresLimits.get(selectedFeature)).setNewMin(newMin);
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
                _featuresLimits.stream().map(AbstractFeatureLimits::clone).collect(Collectors.toList()));
        ((NumericFeatureLimits) clone._featuresLimits.get(selectedFeature)).setNewMax(newMax);
        return clone;
    }

    /**
     * Creates new instance of limits with updated mask - replaces old mask with new more precise one.
     *
     * @param selectedFeature feature index to update mask
     * @param newMask          new mask for the feature
     * @return clone with updated mask
     */
    public DataFeaturesLimits updateMask(final int selectedFeature, final boolean[] newMask) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(AbstractFeatureLimits::clone).collect(Collectors.toList()));
        ((CategoricalFeatureLimits) clone._featuresLimits.get(selectedFeature)).setNewMask(newMask);
        return clone;
    }

    /**
     * Creates new instance of limits with updated mask - excludes from the current mask categories defined by the new one.
     *
     * @param selectedFeature feature index to update mask
     * @param maskToExclude          new mask for the feature
     * @return clone with updated mask
     */
    public DataFeaturesLimits updateMaskExcluded(int selectedFeature, boolean[] maskToExclude) {
        DataFeaturesLimits clone = new DataFeaturesLimits(
                _featuresLimits.stream().map(AbstractFeatureLimits::clone).collect(Collectors.toList()));
        ((CategoricalFeatureLimits) clone._featuresLimits.get(selectedFeature)).setNewMaskExcluded(maskToExclude);
        return clone;
    }
    
    public AbstractFeatureLimits getFeatureLimits(int featureIndex) {
        return _featuresLimits.get(featureIndex);
    }

    /**
     * Serialize limits to 2D double array depending on the features types, so it can be passed to MR task
     *
     * @return
     */
    public double[][] toDoubles() {
        return _featuresLimits.stream()
                .map(AbstractFeatureLimits::toDoubles)
                .toArray(double[][]::new);
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
