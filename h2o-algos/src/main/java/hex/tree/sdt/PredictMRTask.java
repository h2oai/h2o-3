package hex.tree.sdt;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;


// todo - adapt for any number of classes, now it is binary
public class PredictMRTask extends MRTask<PredictMRTask> {
    // output - frame with one column - class
    private final double[][] compressedTree;

    public PredictMRTask(final double[][] compressedTree) {
        this.compressedTree = compressedTree;
    }

    private int predictRowStartingFromNode(final double[] rowValues, final int actualNodeIndex) {
        double featureOrDummy = compressedTree[actualNodeIndex][0];
        double thresholdOrValue = compressedTree[actualNodeIndex][1];
        // first value is dummy -1 means that the node is list, return value of list
        if (featureOrDummy == -1) {
            return (int) thresholdOrValue;
        }

        if (rowValues[(int) featureOrDummy] <= thresholdOrValue) {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 1);
        } else {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 2);
        }
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
            double[] rowValues = new double[cs.length];
            for (int i = 0; i < cs.length; i++) {
                rowValues[i] = cs[i].atd(row);
            }
            nc[0].addNum(predictRowStartingFromNode(rowValues, 0));
        }
    }
}
