package hex.tree.sdt.mrtasks;

import org.apache.commons.math3.util.Precision;
import water.MRTask;
import water.fvec.Chunk;

/**
 * MR task for counting classes.
 */
public class GetClassCountsMRTask extends MRTask<GetClassCountsMRTask> {
    public int _numClasses;
    // counts of samples for each class, class corresponds to the index in array: [count0, count1, ...]
    public int[] _countsByClass;

    private final double[][] _featuresLimits;

    int LIMIT_MIN = 0;
    int LIMIT_MAX = 1;

    public GetClassCountsMRTask(double[][] featuresLimits, int numClasses) {
        _numClasses = numClasses;
        _countsByClass = new int[_numClasses];
        _featuresLimits = featuresLimits;
    }

    @Override
    public void map(Chunk[] cs) {
        int classColumn = cs.length - 1; // the last column
        int numRows = cs[0]._len;
        boolean conditionsFailed;
        // select only rows that fulfill all conditions
        for (int row = 0; row < numRows; row++) {
            conditionsFailed = false;
            // - 1 because of the class column - don't check limits on it
            for (int column = 0; column < cs.length - 1 /*exclude prediction column*/; column++) {
                // if the value is out of the given limit, skip this row
                if (cs[column].atd(row) <= _featuresLimits[column][LIMIT_MIN]
                        || cs[column].atd(row) > _featuresLimits[column][LIMIT_MAX]) {
                    conditionsFailed = true;
                    break;
                }
            }
            if (!conditionsFailed) {
                for (int c = 0; c < _numClasses; c++) {
                    if (Precision.equals(cs[classColumn].atd(row), c, Precision.EPSILON)) {
                        _countsByClass[c]++;
                    }
                }
            }
        }
    }

    @Override
    public void reduce(GetClassCountsMRTask mrt) {
        for (int c = 0; c < _numClasses; c++) {
            _countsByClass[c] += mrt._countsByClass[c];
        }
    }
}
