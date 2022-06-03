package hex.tree.sdt.mrtasks;


import org.apache.commons.math3.util.Precision;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;


public class SplitFrameMRTask extends MRTask<SplitFrameMRTask> {
    int feature;
    double threshold;
    /**
     * 0 for left split, 1 for right split.
     */
    int leftRightSplit;

    public SplitFrameMRTask(int feature, double threshold, int leftRightSplit) {
        this.feature = feature;
        this.threshold = threshold;
        this.leftRightSplit = leftRightSplit;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        int numCols = cs.length;
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
            if ((leftRightSplit == 0
                    && (cs[feature].atd(row) < threshold
                    || Precision.equals(cs[feature].atd(row), threshold, 0.000001d)))
                    || (leftRightSplit == 1 && cs[feature].atd(row) > threshold)) {
                for (int column = 0; column < numCols; column++) {
                    nc[column].addNum(cs[column].atd(row));
                }
            }
        }
    }

}


    
