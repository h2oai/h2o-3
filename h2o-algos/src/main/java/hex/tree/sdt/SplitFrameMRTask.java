package hex.tree.sdt;


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
            if ((leftRightSplit == 0 && cs[feature].atd(row) <= threshold)
            || (leftRightSplit == 1 && cs[feature].atd(row) > threshold)) {
                for(int column = 0; column < numCols; column ++) {
                    nc[column].addNum(cs[column].atd(row));
                }
            } 
        }
    }
    
//    @Override
//    public void map(Chunk[] cs, NewChunk[] nc) {
//        int numCols = cs.length;
//        int numRows = cs[0]._len;
//        int[] splits = new int[numRows];
//        int currentIndexLeft = 0;
//        int currentIndexRight = numRows - 1;
//        for (int row = 0; row < numRows; row++) {
//            if (cs[feature].atd(row) <= threshold) {
//                splits[currentIndexLeft] = row;
//                currentIndexLeft ++;
//            } else {
//                splits[currentIndexRight] = row;
//                currentIndexRight --;
//            }
//        }
//        int[] leftSplitArr = new int[currentIndexLeft];
//        int[] rightSplitArr = new int[numRows - currentIndexRight - 1]; // todo - test it
//        System.arraycopy(splits, 0, leftSplitArr, 0, leftSplitArr.length);
//        System.arraycopy(splits, currentIndexLeft, rightSplitArr, 0, rightSplitArr.length);
//        for (int col = 0; col < numCols; col++) {
//            cs[col].extractRows(nc[col], leftSplitArr);
//            cs[col].extractRows(nc[numCols + col], rightSplitArr);
//        }
//
//    }
}


    
