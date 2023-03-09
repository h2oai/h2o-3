package hex.tree.sdt.binning;

import hex.tree.sdt.DataFeaturesLimits;
import hex.tree.sdt.mrtasks.FeaturesLimitsMRTask;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.tree.sdt.binning.BinningStrategy.CATEGORICAL;

public class Histogram {
    private final List<FeatureBins> _featuresBins;
    private final BinningStrategy _binningStrategy;

    public Histogram(Frame originData, DataFeaturesLimits conditionLimits, BinningStrategy binningStrategy) {
        _binningStrategy = binningStrategy;
        // get real features limits where the conditions are fulfilled
        DataFeaturesLimits featuresLimitsForConditions = getFeaturesLimitsForConditions(originData, conditionLimits);
        // call strategy to create bins for each feature separately
        _featuresBins = IntStream
                .range(0, originData.numCols() - 1/*exclude the last prediction column*/)
                .mapToObj(i -> (originData.vec(i).get_type() == Vec.T_NUM)
                        ? new FeatureBins(_binningStrategy.createFeatureBins(originData, featuresLimitsForConditions, i))
                        : new FeatureBins(CATEGORICAL.createFeatureBins(originData, featuresLimitsForConditions, i)))
                .collect(Collectors.toList());
    }


    /**
     * Get list of feature bins (copy) - for testing.
     *
     * @param featureIndex feature index
     * @return list of feature bins
     */
    public List<NumericBin> getFeatureBins(int featureIndex) {
        return _featuresBins.get(featureIndex).getFeatureBins();
    }

    public int featuresCount() {
        return _featuresBins.size();
    }

    /**
     * Computes features limits considering known condition limits of ancestors.
     * For example: what are real limits of all features considering that feature x is limited by values x_i and x_j.
     *
     * @return new features limits
     */
    public static DataFeaturesLimits getFeaturesLimitsForConditions(Frame originData, DataFeaturesLimits conditionLimits) {
        FeaturesLimitsMRTask task = new FeaturesLimitsMRTask(conditionLimits == null
                ? DataFeaturesLimits.defaultLimits(originData.numCols())
                : conditionLimits.toDoubles());
        task.doAll(originData);
        return new DataFeaturesLimits(task._realFeatureLimits);
    }

    public List<BinAccumulatedStatistics> calculateBinsStatisticsForFeature(int feature) {
        return _featuresBins.get(feature).calculateBinsStatisticsForFeature();
    }

    public boolean isConstant(int featureIndex) {
        return _featuresBins.get(featureIndex).isConstant();
    }
}
