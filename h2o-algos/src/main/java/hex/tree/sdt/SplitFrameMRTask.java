package hex.tree.sdt;


import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.ArrayList;

public class SplitFrameMRTask extends MRTask<SplitFrameMRTask> {
    int feature;
    double threshold;

    public SplitFrameMRTask(int feature, double threshold) {
        this.feature = feature;
        this.threshold = threshold;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        int numCols = cs.length;
        int numRows = cs[0]._len;
        ArrayList<Integer> leftSplit = new ArrayList<>();
        ArrayList<Integer> rightSplit = new ArrayList<>();
        int currentIndex = 0;
        for (int row = 0; row < numRows; row++) {
            if (cs[feature].atd(row) <= threshold) {
//                    leftSplit[currentIndex] = row;
                leftSplit.add(row);
            } else {
//                    rightSplit[currentIndex] = row;
                rightSplit.add(row);
            }
        }
        int[] leftSplitArr = new int[leftSplit.size()];
        for(int i = 0; i < leftSplit.size(); i ++) {
            leftSplitArr[i] = leftSplit.get(i);
        }

        int[] rightSplitArr = new int[rightSplit.size()];
        for(int i = 0; i < rightSplit.size(); i ++) {
            rightSplitArr[i] = rightSplit.get(i);
        }

//            System.out.println(Arrays.toString(leftSplitArr));
        for (int col = 0; col < numCols; col++) {
            cs[col].extractRows(nc[col], leftSplitArr);
            cs[col].extractRows(nc[numCols + col], rightSplitArr);
//                System.out.println("cs" + cs[col].atd(0) + "," + cs[col].atd(1) + "," + cs[col].atd(2) + "," + cs[col].atd(3));
//                System.out.println(nc[col].atd(0) + "," + nc[col].atd(1) + "," + nc[col].atd(2) + "," + nc[col].atd(3));
        }

    }
}


    
