package hex.tree.sdt.mrtasks;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

/**
 * MR task for calculating real features limits based on limits. Useful optimization equal-width binning.
 */
public class FeaturesLimitsMRTask extends MRTask<FeaturesLimitsMRTask> {

    // numCol x 2 - min and max for each feature
    double[][] _featuresLimits;
    // numCol x 2 - min and max for each feature - size is ok as it is linearly dependent on numCols
    public double[][] _realFeatureLimits;

    // index of min
    int LIMIT_MIN = 0;

    // index of max
    int LIMIT_MAX = 1;

    public FeaturesLimitsMRTask(double[][] featuresLimits) {
        _featuresLimits = featuresLimits;
        // Init value of max is min and init value of min is max so any real value is better than the init one
        _realFeatureLimits = Arrays.stream(featuresLimits)
                .map(f -> new double[]{Double.MAX_VALUE, Double.MIN_VALUE})
                .toArray(double[][]::new);
    }

    /**
     * Update current minimum if the candidate value is less than the actual minimum.
     *
     * @param feature        feature index
     * @param candidateValue new potential min
     */
    private void tryUpdatingMin(int feature, double candidateValue) {
        if (_realFeatureLimits[feature][LIMIT_MIN] > candidateValue) {
            _realFeatureLimits[feature][LIMIT_MIN] = candidateValue - 0.0001;
        }
    }

    /**
     * Update current maximum if the candidate value is grater than the actual maximum.
     *
     * @param feature        feature index
     * @param candidateValue new potential max
     */
    private void tryUpdatingMax(int feature, double candidateValue) {
        if (_realFeatureLimits[feature][LIMIT_MAX] < candidateValue) {
            _realFeatureLimits[feature][LIMIT_MAX] = candidateValue;
        }
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        int numCols = cs.length;
        int numRows = cs[0]._len;
        boolean conditionsFailed;
        // select only rows that fulfill all conditions
        for (int row = 0; row < numRows; row++) {
            conditionsFailed = false;
            // - 1 because of the class column - don't check limits on it
            for (int column = 0; column < numCols - 1; column++) {
                // if the value is out of the given limit, skip this row
                if ((cs[column].atd(row) <= _featuresLimits[column][LIMIT_MIN])
                        || (cs[column].atd(row) > _featuresLimits[column][LIMIT_MAX])) {
                    conditionsFailed = true;
                    break;
                }
            }
            if (!conditionsFailed) {
                // find min and max for each feature for selected rows only
                for (int column = 0; column < numCols; column++) {
                    tryUpdatingMin(column, cs[column].atd(row));
                    tryUpdatingMax(column, cs[column].atd(row));
                }
            }
        }
    }

    @Override
    public void reduce(FeaturesLimitsMRTask mrt) {
        for (int column = 0; column < _featuresLimits.length; column++) {
            tryUpdatingMin(column, mrt._realFeatureLimits[column][LIMIT_MIN]);
            tryUpdatingMax(column, mrt._realFeatureLimits[column][LIMIT_MAX]);
        }
    }

}
