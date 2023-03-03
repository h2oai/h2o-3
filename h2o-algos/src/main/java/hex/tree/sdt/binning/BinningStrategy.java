package hex.tree.sdt.binning;

import hex.tree.sdt.DataFeaturesLimits;
import hex.tree.sdt.NumericFeatureLimits;
import hex.tree.sdt.mrtasks.CountBinSamplesCountMRTask;
import water.fvec.Frame;
import water.util.Log;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Strategy for binning. Creates bins for single feature.
 */
public enum BinningStrategy {
    
    /**
     * Equal width: (max - min) / num_bins, optimized. Min is always excluded.
     */
    EQUAL_WIDTH {

        public final int NUM_BINS = 10;

        public final int DECIMALS_TO_CONSIDER = 2;

        double computeExponentForTheFeature(double step) {
            return Math.log10(step);
        }

        double roundToNDecimalPoints(double number, int decimals) {
            BigDecimal bigDecimal = new BigDecimal(number);
            return bigDecimal.setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        }

        double roundToNDecimalPoints(double number) {
            return roundToNDecimalPoints(number, DECIMALS_TO_CONSIDER);
        }

        private List<NumericBin> createEmptyBinsFromBinningValues(List<Double> binningValues, double realMin, double realMax) {
            List<NumericBin> emptyBins = new ArrayList<>();
            // create bins between nearest binning values, don't create bin starting with the last value (on index size - 1)
            for (int i = 0; i < binningValues.size() - 1; i++) {
                emptyBins.add(
                        new NumericBin(roundToNDecimalPoints(binningValues.get(i)),
                                roundToNDecimalPoints(binningValues.get(i + 1))));
            }
            // set the firs min to some lower value (relative to step) so the actual value equal to min is not lost
            emptyBins.get(0)._min = realMin - 0.0001 * (binningValues.get(1) - binningValues.get(0));
            // set the last max to the real max value to avoid precision troubles
            emptyBins.get(emptyBins.size() - 1)._max = realMax;
            return emptyBins;
        }
        
        @Override
        List<NumericBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            NumericFeatureLimits featureLimits = (NumericFeatureLimits) featuresLimits.getFeatureLimits(feature);
            double step = (featureLimits._max - featureLimits._min) / NUM_BINS;
            // constant feature - dont use for split
            if (step == 0) {
                return null;
            }
            double stepExponent = computeExponentForTheFeature(step);
            // if step is grater than one, remove decimal part (round to the nearest int)
            if (stepExponent > 0) {
                step = Math.round(step);
            } else {
                // if step is more than 2 positions less precise than selected decimal limit, 
                // set lower decimal limit to work with lower values
                if (stepExponent < DECIMALS_TO_CONSIDER * (-1) + 2) {
                    int newDecimalsToConsider = (int) Math.floor(stepExponent) * (-1) + 2;
                    Log.debug("DECIMALS_TO_CONSIDER: " + newDecimalsToConsider);
                    step = roundToNDecimalPoints(step, newDecimalsToConsider);
                } else {
                    step = roundToNDecimalPoints(step, DECIMALS_TO_CONSIDER);
                }
            }
            Log.debug("New step: " + step);
            // get thresholds which are minimums and maximums of bins (including min amd max)
            List<Double> binningValues = new ArrayList<>();
            for (double value = featureLimits._min; value <= featureLimits._max; value += step) {
                binningValues.add(value);
            }
            List<NumericBin> emptyBins = createEmptyBinsFromBinningValues(
                    binningValues, featureLimits._min, featureLimits._max);
            
            Log.debug("Real max: " + featureLimits._max
                    + ", last bin min: " + emptyBins.get(emptyBins.size() - 1)._min
                    + ", last bin max: " + emptyBins.get(emptyBins.size() - 1)._max + ", step: " + step);


            // todo categorical data - no binning is needed for categorical data as they are already binned.
            // but the statistics have to be calculated...


            return calculateBinSamplesCount(originData, emptyBins, feature, featuresLimits.toDoubles());
        }
    },

    /**
     * Equal height: bins have approximately the same size - todo 
     * - probably too costly to do it with MR task, better leave equal-width
     */
    EQUAL_HEIGHT {
        @Override
        List<NumericBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            return null;
        }

    },

    /**
     * Custom bins: works with provided bins limits - todo
     */
    CUSTOM_BINS {
        @Override
        List<NumericBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            return null;
        }
    },

    CATEGORICAL {
        @Override
        List<NumericBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
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
    abstract List<NumericBin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature);

    public static List<NumericBin> calculateBinSamplesCount(Frame data, List<NumericBin> emptyBins,
                                                            int feature, double[][] featuresLimits) {
        // run MR task to compute accumulated statistic for bins - one task for each bin
        return emptyBins.stream().peek(bin -> {
            CountBinSamplesCountMRTask task =
                    new CountBinSamplesCountMRTask(feature, bin._min, bin._max, featuresLimits);
            task.doAll(data);
            bin._count0 = task._count0;
            bin._count = task._count;
        }).collect(Collectors.toList());


    }
}
    
