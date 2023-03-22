package hex.tree.dt.mrtasks;

import org.apache.commons.math3.util.Precision;
import water.MRTask;
import water.fvec.Chunk;

/**
 * MR task for counting samples in bins.
 */
public class CountBinsSamplesCountsMRTask extends MRTask<CountBinsSamplesCountsMRTask> {
    public int _featureSplit;
    // numCol x 2 - min and max for each feature
    double[][] _featuresLimits;
    // binsCount * 4 - min, max, count, count0
    public double[][] _bins;


    public static final int LIMIT_MIN = 0;
    public static final int LIMIT_MAX = 1;
    public static final int COUNT = 2;
    public static final int COUNT_0 = 3;

    public CountBinsSamplesCountsMRTask(int featureSplit, double[][] featuresLimits, double[][] bins) {
        _featureSplit = featureSplit;
        _featuresLimits = featuresLimits;
        _bins = bins;
    }

    @Override
    public void map(Chunk[] cs) {
        int classFeature = cs.length - 1;
        int numRows = cs[0]._len;
        boolean conditionsFailed;
        // select only rows that fulfill all conditions
        for (int row = 0; row < numRows; row++) {
            conditionsFailed = false;
            for (int column = 0; column < cs.length - 1 /*exclude prediction column*/; column++) {
                // if the value is out of the given limit, skip this row
                if (cs[column].atd(row) <= _featuresLimits[column][LIMIT_MIN]
                        || cs[column].atd(row) > _featuresLimits[column][LIMIT_MAX]) {
                    conditionsFailed = true;
                    break;
                }
            }
            if (!conditionsFailed) {
                for (int i = 0; i < _bins.length; i++) {
                    // count feature values in the current bin
                    if ((cs[_featureSplit].atd(row) > _bins[i][LIMIT_MIN])
                            && (cs[_featureSplit].atd(row) < _bins[i][LIMIT_MAX]
                            || Precision.equals(cs[_featureSplit].atd(row), _bins[i][LIMIT_MAX], Precision.EPSILON))) {
                        _bins[i][COUNT] ++;
                        if (Precision.equals(cs[classFeature].atd(row), 0, Precision.EPSILON)) {
                            _bins[i][COUNT_0] ++;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void reduce(CountBinsSamplesCountsMRTask mrt) {
        for (int i = 0; i < _bins.length; i++) {
            _bins[i][COUNT] += mrt._bins[i][COUNT];
            _bins[i][COUNT_0] += mrt._bins[i][COUNT_0];
        }
    }
}
