package hex.tree.sdt.mrtasks;

import org.apache.commons.math3.util.Precision;
import water.MRTask;
import water.fvec.Chunk;

public class GetClassCountsMRTask extends MRTask<GetClassCountsMRTask> {
    public int _count0;
    public int _count1;
    
    private final double[][] _featuresLimits;

    int LIMIT_MIN = 0;
    int LIMIT_MAX = 1;

    public GetClassCountsMRTask(double[][] featuresLimits) {
        _count0 = 0;
        _count1 = 0;
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
            for (int column = 0; column < cs.length; column++) {
                // if the value is out of the given limit, skip this row
                if (cs[column].atd(row) < _featuresLimits[column][LIMIT_MIN]
                        || cs[column].atd(row) > _featuresLimits[column][LIMIT_MAX]) {
                    conditionsFailed = true;
                    break;
                }
            }
            if (!conditionsFailed) {
                if (Precision.equals(cs[classColumn].atd(row), 0, 0.000001d)) { // todo - not just 0 and 1
                    _count0++;
                } else {
                    _count1++;
                }
            }
        }
    }

    @Override
    public void reduce(GetClassCountsMRTask mrt) {
        _count0 += mrt._count0;
        _count1 += mrt._count1;
    }
}
