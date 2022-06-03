package hex.tree.sdt.binning;

import hex.tree.sdt.DataFeaturesLimits;
import hex.tree.sdt.FeatureLimits;
import hex.tree.sdt.mrtasks.CountBinningStatisticsMRTask;
import water.fvec.Frame;
import water.util.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public enum BinningStrategy {

    EQUAL_WIDTH {

        public final int VALUES_COUNT_IN_RANGE = 100;

        public int DECIMALS_TO_CONSIDER = 2;

        double computeExponentForTheFeature(double step) {
            return Math.log10(step);
        }

        double roundToNDecimalPoints(double number, int decimals) {
            BigDecimal bigDecimal = new BigDecimal(number);
            return bigDecimal.setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        }


        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
//            System.out.println("Executing strategy A");
            FeatureLimits featureLimits = featuresLimits.getFeatureLimits(feature);
            double step = (featureLimits._max - featureLimits._min) / VALUES_COUNT_IN_RANGE;
            // constant feature - dont use for split
            if(step == 0) {
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
                    DECIMALS_TO_CONSIDER = (int) Math.floor(stepExponent) * (-1) + 2;
//                    System.out.println("DECIMALS_TO_CONSIDER: " + DECIMALS_TO_CONSIDER);
                }
                step = roundToNDecimalPoints(step, DECIMALS_TO_CONSIDER);
            }
//            System.out.println("New step: " + step);
            // get thresholds which are maximums of bins
            double finalStep = step;
            List<Double> binningValues = new ArrayList<>();
            for (double value = featureLimits._min + step; value < featureLimits._max; value += step) {
                binningValues.add(value);
            }
            List<Bin> emptyBins = binningValues.stream()
                    .map(v -> roundToNDecimalPoints(v, DECIMALS_TO_CONSIDER))
                    // get bins - calculate minimum value and construct bin for each previously computed maximum value
                    .map(v -> new Pair<>(v - finalStep, v)).map(Bin::new).collect(Collectors.toList());
            // set the firs min to some lower value so the actual value equal to min is not lost
            emptyBins.get(0)._min = emptyBins.get(0)._min - 0.1 * step;
            // set the last max to the real max value to avoid precision troubles
            emptyBins.get(emptyBins.size() - 1)._max = featureLimits._max;
//            System.out.println("Real max: " + featureLimits._max 
//                    + ", last bin min: " + emptyBins.get(emptyBins.size() - 1)._min 
//                    + ", last bin max: " + emptyBins.get(emptyBins.size() - 1)._max + ", step: " + step);

            // run MR task to compute accumulated statistic for bins
            return emptyBins.stream().peek(bin -> {
                CountBinningStatisticsMRTask task = new CountBinningStatisticsMRTask(feature, bin._min, bin._max,
                        featuresLimits.toDoubles());
                task.doAll(originData);
                bin._count0 = task._count0;
                bin._count = task._count;
            }).collect(Collectors.toList());
        }
    },

    EQUAL_HEIGHT {
        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            return null;
        }

    },

    CUSTOM_BINS {
        @Override
        List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature) {
            return null;
        }
    };

    abstract List<Bin> createFeatureBins(Frame originData, DataFeaturesLimits featuresLimits, int feature);

}
    
