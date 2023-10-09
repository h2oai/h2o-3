package hex.tree.dt.mrtasks;

import hex.tree.dt.DT;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

import static hex.tree.dt.NumericFeatureLimits.*;

/**
 * MR task for calculating real features limits based on limits. Useful optimization equal-width binning.
 */
public class FeaturesLimitsMRTask extends MRTask<FeaturesLimitsMRTask> {

    // numCol x 2 - min and max for each feature
    double[][] _featuresLimits;
    // numCol x 2 - min and max for each feature - size is ok as it is linearly dependent on numCols
    public double[][] _realFeatureLimits;


    public FeaturesLimitsMRTask(double[][] featuresLimits) {
        _featuresLimits = featuresLimits;
        _realFeatureLimits = null;
    }

    /**
     * Update current minimum if the candidate value is less than the actual minimum.
     *
     * @param feature        feature index
     * @param candidateValue new potential min
     */
    private void tryUpdatingMin(int feature, double candidateValue) {
        if (_realFeatureLimits[feature][LIMIT_MIN] > candidateValue) {
            _realFeatureLimits[feature][LIMIT_MIN] = candidateValue - DT.EPSILON;
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


    /**
     * Mark new category - set the flag of the given category to 1.0 (true).
     *
     * @param feature  feature index
     * @param category new category
     */
    private void tryAddingCategory(int feature, double category) {
        _realFeatureLimits[feature][(int) category] = 1.0;
    }

    /**
     * Update current categories mask with given categories mask.
     *
     * @param feature        feature index
     * @param categoriesMask categories to add to existing mask
     */
    private void updateCategories(int feature, double[] categoriesMask) {
        for (int i = 0; i < categoriesMask.length; i++) {
            // set 1.0 (true) even if it is already 1.0
            if (categoriesMask[i] == 1.0) {
                _realFeatureLimits[feature][i] = 1.0;
            }
        }
    }


    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        // init real features limits - check if the feature is numerical or categorical
        _realFeatureLimits = Arrays.stream(_featuresLimits)
                .map(f -> f[NUMERICAL_FLAG] == -1.0
                        // Init max with min double and init min with max double so any real value is better than the init one
                        ? new double[]{-1.0, Double.MAX_VALUE, (-1) * Double.MAX_VALUE}
                        // Init with zeros to fill with present categories
                        : new double[f.length])
                .toArray(double[][]::new);

        int numCols = cs.length - 1; // exclude prediction column
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
            // update limits for each feature for rows that satisfy previous condition
            if (!conditionsFailed) {
                for (int column = 0; column < numCols; column++) {
                    if (_featuresLimits[column][NUMERICAL_FLAG] == -1.0) {
                        // numerical feature
                        tryUpdatingMin(column, cs[column].atd(row));
                        tryUpdatingMax(column, cs[column].atd(row));
                    } else {
                        // categorical feature
                        tryAddingCategory(column, cs[column].atd(row));
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
    
    @Override
    public void reduce(FeaturesLimitsMRTask mrt) {
        for (int column = 0; column < _featuresLimits.length; column++) {
            if (_realFeatureLimits[column][NUMERICAL_FLAG] == -1.0) {
                tryUpdatingMin(column, mrt._realFeatureLimits[column][LIMIT_MIN]);
                tryUpdatingMax(column, mrt._realFeatureLimits[column][LIMIT_MAX]);
            } else {
                updateCategories(column, mrt._realFeatureLimits[column]);
            }
        }
    }

}
