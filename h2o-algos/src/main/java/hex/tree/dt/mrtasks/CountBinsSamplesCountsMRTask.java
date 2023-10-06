package hex.tree.dt.mrtasks;

import org.apache.commons.math3.util.Precision;
import water.MRTask;
import water.fvec.Chunk;

import java.util.Arrays;

import static hex.tree.dt.NumericFeatureLimits.LIMIT_MAX;
import static hex.tree.dt.NumericFeatureLimits.LIMIT_MIN;
import static hex.tree.dt.binning.NumericBin.*;


/**
 * MR task for counting samples in bins.
 */
public class CountBinsSamplesCountsMRTask extends MRTask<CountBinsSamplesCountsMRTask> {
    public final int _featureSplit;
    // numCol x 2 - min and max for each feature
    final double[][] _featuresLimits;
    // binsCount x bin_encoding_len (5 or 3), depending on feature type:
    // for numeric feature bin_encoding_len = 5:      {numeric flag (-1.0), count, count0, min, max}
    // for categorical feature bin_encoding_len = 3:  {category, count, count0}
    public double[][] _bins;

    // indices for the serialized array
    public static final int NUMERICAL_FLAG = 0;

    // for both numeric and categorical features indices of count and count0 are the same
    public static final int COUNT = 1;
    public static final int COUNT_0 = 2;


    public CountBinsSamplesCountsMRTask(int featureSplit, double[][] featuresLimits, double[][] bins) {
        _featureSplit = featureSplit;
        _featuresLimits = featuresLimits;
        _bins = bins;
    }

    @Override
    public void map(Chunk[] cs) {
        // deep copy of bins array so the reduce phase performs correctly
        {
            double[][] tmpBins = new double[_bins.length][];
            for (int b = 0; b < _bins.length; b++) {
                tmpBins[b] = Arrays.copyOf(_bins[b], _bins[b].length);
            }
            _bins = tmpBins;
        }
        int classFeature = cs.length - 1;
        int numRows = cs[0]._len;
        boolean conditionsFailed;
        // select only rows that fulfill all conditions
        for (int row = 0; row < numRows; row++) {
            conditionsFailed = false;
            for (int column = 0; column < cs.length - 1 /*exclude prediction column*/; column++) {
                if (!verifyLimits(cs[column].atd(row), column)) {
                    conditionsFailed = true;
                    break;
                }
            }
            if (!conditionsFailed) {
                if (!isNumerical(_featureSplit)) {
                    for (int i = 0; i < _bins.length; i++) {
                        // find bin by category
                        if (_bins[i][0] == cs[_featureSplit].atd(row)) {
                            _bins[i][COUNT]++;
                            if (Precision.equals(cs[classFeature].atd(row), 0, Precision.EPSILON)) {
                                _bins[i][COUNT_0]++;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < _bins.length; i++) {
                        // count feature values in the current bin
                        if (checkBinBelonging(cs[_featureSplit].atd(row), i)) {
                            _bins[i][COUNT]++;
                            if (Precision.equals(cs[classFeature].atd(row), 0, Precision.EPSILON)) {
                                _bins[i][COUNT_0]++;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isNumerical(int feature) {
        return _featuresLimits[feature][NUMERICAL_FLAG] == -1.0;
    }

    private boolean verifyLimits(double featureValue, int column) {
        // verifying limits is different for numerical and categorical columns
        if (isNumerical(column)) {
            return featureValue > _featuresLimits[column][LIMIT_MIN]
                    && featureValue <= _featuresLimits[column][LIMIT_MAX];
        } else {
            // actual categorical value is true(1.0) in feature limits
            return _featuresLimits[column][(int) featureValue] == 1.0;
        }
    }

    private boolean checkBinBelonging(double featureValue, int bin) {
        return featureValue > _bins[bin][MIN_INDEX] && featureValue <= _bins[bin][MAX_INDEX];
    }

    @Override
    public void reduce(CountBinsSamplesCountsMRTask mrt) {
        for (int i = 0; i < _bins.length; i++) {
            _bins[i][COUNT] += mrt._bins[i][COUNT];
            _bins[i][COUNT_0] += mrt._bins[i][COUNT_0];
        }
    }
}
