package hex.tree.dt.binning;

import hex.tree.dt.CategoricalFeatureLimits;
import hex.tree.dt.DataFeaturesLimits;
import hex.tree.dt.NumericFeatureLimits;
import hex.tree.dt.mrtasks.CountBinsSamplesCountsMRTask;
import water.fvec.Frame;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * Strategy for binning. Create bins for single feature.
 */
public enum BinningStrategy {
    
    /**
     * Equal width: (max - min) / num_bins, optimized. Min is always excluded.
     */
    EQUAL_WIDTH {

        public final int NUM_BINS = 10;

        public final int DECIMALS_TO_CONSIDER = 2;
        
        public final double MIN_REL_COEFF = 0.0001;

        double roundToNDecimalPoints(double number, int decimals) {
            BigDecimal bigDecimal = new BigDecimal(number);
            return bigDecimal.setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        }

        double roundToNDecimalPoints(double number) {
            return roundToNDecimalPoints(number, DECIMALS_TO_CONSIDER);
        }

        private List<AbstractBin> createEmptyBinsFromBinningValues(List<Double> binningValues, double realMin, double realMax, int nclass) {
            List<AbstractBin> emptyBins = new ArrayList<>();
            // create bins between nearest binning values, don't create bin starting with the last value (on index size - 1)
            for (int i = 0; i < binningValues.size() - 1; i++) {
                emptyBins.add(
                        new NumericBin(roundToNDecimalPoints(binningValues.get(i)),
                                roundToNDecimalPoints(binningValues.get(i + 1)), nclass));
            }
            // set the firs min to some lower value (relative to step) so the actual value equal to min is not lost
            ((NumericBin) emptyBins.get(0)).setMin(realMin - MIN_REL_COEFF * (binningValues.get(1) - binningValues.get(0)));
            // set the last max to the real max value to avoid precision troubles
            ((NumericBin) emptyBins.get(emptyBins.size() - 1)).setMax(realMax);
            return emptyBins;
        }
        
        @Override
        List<AbstractBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature, int nclass) {
            if (originData.vec(feature).isNumeric()) {
                NumericFeatureLimits featureLimits = (NumericFeatureLimits) featuresLimits.getFeatureLimits(feature);
                double step = (featureLimits._max - featureLimits._min) / NUM_BINS;
                // constant feature - dont use for split
                if (step == 0) {
                    return null;
                }
                // get thresholds which are minimums and maximums of bins (including min amd max)
                List<Double> binningValues = new ArrayList<>();
                for (double value = featureLimits._min; value <= featureLimits._max; value += step) {
                    binningValues.add(value);
                }
                List<AbstractBin> emptyBins = createEmptyBinsFromBinningValues(
                        binningValues, featureLimits._min, featureLimits._max, nclass);

                return calculateNumericBinSamplesCount(originData, emptyBins, featuresLimits.toDoubles(), feature);
            } else {
                CategoricalFeatureLimits featureLimits = (CategoricalFeatureLimits) featuresLimits.getFeatureLimits(feature);
                List<AbstractBin> emptyBins = new ArrayList<>();
                for (int category = 0; category < featureLimits._mask.length; category++) {
                    // if the category is present in feature values, add new bin for this category
                    if (featureLimits._mask[category]) {
                        emptyBins.add(new CategoricalBin(category, nclass));
                    }
                }

                return calculateCategoricalBinSamplesCount(originData, emptyBins, featuresLimits.toDoubles(), feature);
            }
        }


    },

    /**
     * Equal height: bins have approximately the same size (not implemented yet)
     * - probably too costly to do it with MR task, better leave equal-width
     */
    EQUAL_HEIGHT {
        @Override
        List<AbstractBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature, int nclass) {
            return null;
        }

    },

    /**
     * Custom bins: works with provided bins limits (not implemented yet)
     */
    CUSTOM_BINS {
        @Override
        List<AbstractBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature, int nclass) {
            return null;
        }
    };
    


    /**
     * Creates bins for selected feature.
     *
     * @param originData     data - not modified
     * @param featuresLimits limits for features
     * @param feature        selected feature index
     * @return list of created bins
     */
    abstract List<AbstractBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature, int nclass);

    /**
     * Calculates samples count for given bins for categorical feature.
     *
     * @param data     data - not modified
     * @param bins     empty bins to calculate samples
     * @param featuresLimits limits for features
     * @param feature        selected feature index
     * @return list of created bins
     */
    private static List<AbstractBin> calculateCategoricalBinSamplesCount(Frame data, List<AbstractBin> bins,
                                                                         double[][] featuresLimits, int feature) {
        // run MR task to compute accumulated statistic for bins - one task for one feature, calculates all bins at once
        double[][] binsArray = bins.stream().map(AbstractBin::toDoubles).toArray(double[][]::new);
        int countsOffset = CountBinsSamplesCountsMRTask.CAT_COUNT_OFFSET;
        CountBinsSamplesCountsMRTask task = new CountBinsSamplesCountsMRTask(feature, featuresLimits, binsArray, countsOffset);
        task.doAll(data);
        for (int i = 0; i < binsArray.length; i++) {
            bins.get(i)._count = (int) task._bins[i][countsOffset];
            bins.get(i)._classesDistribution =
                    DoubleStream.of(Arrays.copyOfRange(task._bins[i], countsOffset + 1, task._bins[i].length))
                            .mapToInt(c -> (int) c).toArray();
        }
        return bins;
    }

    /**
     * Calculates samples count for given bins for numeric feature.
     *
     * @param data     data - not modified
     * @param bins     empty bins to calculate samples
     * @param featuresLimits limits for features
     * @param feature        selected feature index
     * @return list of created bins
     */
    private static List<AbstractBin> calculateNumericBinSamplesCount(Frame data, List<AbstractBin> bins,
                                                             double[][] featuresLimits, int feature) {
        // run MR task to compute accumulated statistic for bins - one task for one feature, calculates all bins at once
        double[][] binsArray = bins.stream().map(AbstractBin::toDoubles).toArray(double[][]::new);
        int countsOffset = CountBinsSamplesCountsMRTask.NUM_COUNT_OFFSET;
        CountBinsSamplesCountsMRTask task = new CountBinsSamplesCountsMRTask(feature, featuresLimits, binsArray, countsOffset);
        task.doAll(data);

        for (int i = 0; i < binsArray.length; i++) {
            bins.get(i)._count = (int) task._bins[i][countsOffset];
            bins.get(i)._classesDistribution =
                    DoubleStream.of(Arrays.copyOfRange(task._bins[i], countsOffset + 1, task._bins[i].length))
                            .mapToInt(c -> (int) c).toArray();
        }
        return bins;
    }
}
    
