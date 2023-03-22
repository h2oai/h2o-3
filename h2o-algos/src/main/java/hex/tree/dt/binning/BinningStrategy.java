package hex.tree.dt.binning;

import hex.tree.dt.DataFeaturesLimits;
import hex.tree.dt.FeatureLimits;
import hex.tree.dt.mrtasks.CountBinSamplesCountMRTask;
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

        private List<Bin> createEmptyBinsFromBinningValues(List<Double> binningValues, double realMin, double realMax) {
            List<Bin> emptyBins = new ArrayList<>();
            // create bins between nearest binning values, don't create bin starting with the last value (on index size - 1)
            for (int i = 0; i < binningValues.size() - 1; i++) {
                emptyBins.add(
                        new Bin(roundToNDecimalPoints(binningValues.get(i)),
                                roundToNDecimalPoints(binningValues.get(i + 1))));
            }
            // set the firs min to some lower value (relative to step) so the actual value equal to min is not lost
            emptyBins.get(0)._min = realMin - 0.0001 * (binningValues.get(1) - binningValues.get(0));
            // set the last max to the real max value to avoid precision troubles
            emptyBins.get(emptyBins.size() - 1)._max = realMax;
            return emptyBins;
        }
        
        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            FeatureLimits featureLimits = featuresLimits.getFeatureLimits(feature);
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
            List<Bin> emptyBins = createEmptyBinsFromBinningValues(binningValues, featureLimits._min, featureLimits._max);
            
            Log.debug("Real max: " + featureLimits._max
                    + ", last bin min: " + emptyBins.get(emptyBins.size() - 1)._min
                    + ", last bin max: " + emptyBins.get(emptyBins.size() - 1)._max + ", step: " + step);

            return calculateBinSamplesCount(originData, emptyBins, feature, featuresLimits.toDoubles());
        }
    },

    /**
     * Equal height: bins have approximately the same size - todo 
     * - probably too costly to do it with MR task, better leave equal-width
     */
    EQUAL_HEIGHT {
        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            return null;
        }

    },

    /**
     * Custom bins: works with provided bins limits - todo
     */
    CUSTOM_BINS {
        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
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
    abstract List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature);
    public static List<Bin> calculateBinSamplesCount(Frame data, List<Bin> emptyBins,
                                                     int feature, double[][] featuresLimits) {
        // run MR task to compute accumulated statistic for bins - one task for each bin
        return emptyBins.stream().peek(bin -> {
            CountBinSamplesCountMRTask task =
                    new CountBinSamplesCountMRTask(feature, bin._min, bin._max, featuresLimits);
            task.doAll(data);
            bin._count0 = task._count0;
            bin._count = task._count;
        }).collect(Collectors.toList());


//        // run MR task to compute accumulated statistic for bins - one task for one feature
//        double[][] binsArray = 
//                emptyBins.stream().map(bin -> new double[]{bin._min, bin._max, 0, 0}).toArray(double[][]::new);
//        CountBinsSamplesCountsMRTask task = new CountBinsSamplesCountsMRTask(feature, featuresLimits, binsArray);
//        task.doAll(data);
//        for(int i = 0; i < binsArray.length; i ++) {
//            emptyBins.get(i)._count = (int) task._bins[i][COUNT];
//            emptyBins.get(i)._count0 = (int) task._bins[i][COUNT_0];
//        }
//        return emptyBins;
    }
}
    
