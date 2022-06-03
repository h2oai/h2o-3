package hex.tree.sdt.binning;

import hex.tree.sdt.DataFeaturesLimits;
import hex.tree.sdt.mrtasks.FeaturesLimitsMRTask;
import water.fvec.Frame;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Histogram {
    private final List<FeatureBins> _featuresBins;
    private final BinningStrategy _binningStrategy;

    public Histogram(Frame originData, DataFeaturesLimits conditionLimits, BinningStrategy binningStrategy) {
        _binningStrategy = binningStrategy;
        _featuresBins = IntStream.range(0, originData.numCols())
                .mapToObj(i -> new FeatureBins(
                        _binningStrategy.createFeatureBins(originData,
                                getFeaturesLimitsForConditions(originData, conditionLimits), i)))
                .collect(Collectors.toList());
    }

    public int featuresCount() {
        return _featuresBins.size();
    }

    /**
     * Computes features limits considering known condition limits of ancestors.
     * For example: what are real limits of all features considering that feature x is limited by values x_i and x_j.
     *
     * @return
     */
    private DataFeaturesLimits getFeaturesLimitsForConditions(Frame originData, DataFeaturesLimits conditionLimits) {
        FeaturesLimitsMRTask task = new FeaturesLimitsMRTask(conditionLimits == null
                ? Stream.generate(() -> new double[]{Double.MIN_VALUE, Double.MAX_VALUE}).limit(originData.numCols()).toArray(double[][]::new)
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
