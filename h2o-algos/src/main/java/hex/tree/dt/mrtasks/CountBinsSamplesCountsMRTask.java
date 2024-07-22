package hex.tree.dt.mrtasks;

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
    
    // binsCount x bin_encoding_len, depending on feature type:
    // for numeric feature:      {numeric flag (-1.0), min, max, count, count0, count1, count2, ...}
    // for categorical feature:  {category, count, count0, count1, count2, ...}
    // for accessing specific class count use {offset+1+class} index - e.g. for numeric count1: _bins[NUM_COUNT_OFFSET+1+1]
    public double[][] _bins;

    // indices for the serialized array
    public static final int NUMERICAL_FLAG = 0;
    public static final int NUM_COUNT_OFFSET = 3; // follows numeric flag, min and max
    public static final int CAT_COUNT_OFFSET = 1; // follows category
    
    private final int _countsOffset;
    


    public CountBinsSamplesCountsMRTask(int featureSplit, double[][] featuresLimits, double[][] bins, int countsOffset) {
        _featureSplit = featureSplit;
        _featuresLimits = featuresLimits;
        _bins = bins;
        _countsOffset = countsOffset;
    }

    @Override
    public void map(Chunk[] cs) {
        // deep copy of bins array so the reduce phase performs correctly
        {
            double[][] tmpBins = new double[_bins.length][];
            for (int b = 0; b < _bins.length; b++) {
                tmpBins[b] = Arrays.copyOf(_bins[b], _bins[b].length);
                for(int c = _countsOffset; c < _bins[b].length; c++) {
                    tmpBins[b][c] = 0; // set all the counts to 0 - throw away existing counts if any
                }
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
                            _bins[i][_countsOffset]++;
                            // calc index as {offset+1+class}
                            _bins[i][_countsOffset + 1 + (int)cs[classFeature].atd(row)]++;
                        }
                    }
                } else {
                    for (int i = 0; i < _bins.length; i++) {
                        // count feature values in the current bin
                        if (checkBinBelonging(cs[_featureSplit].atd(row), i)) {
                            _bins[i][_countsOffset]++;
                            // calc index as {offset+1+class}
                            _bins[i][_countsOffset + 1 + (int)cs[classFeature].atd(row)]++;
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
            for(int c = _countsOffset; c < _bins[i].length; c++) {
                _bins[i][c] += mrt._bins[i][c];
            }
        }
    }
}
