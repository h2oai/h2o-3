package hex.tree.sdt;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.ArrayList;
import java.util.List;

// todo - adapt for any number of classes, now it is binary
public class PredictMRTask extends MRTask<PredictMRTask> {
    // output - frame with one column - class
    private final Double[][] compressedTree;

    public PredictMRTask(final Double[][] compressedTree) {
        this.compressedTree = compressedTree;
    }

    private int predictRowStartingFromNode(final List<Double> rowValues, final int actualNodeIndex) {
        Double feature = compressedTree[actualNodeIndex][0];
        Double threshold = compressedTree[actualNodeIndex][1];
        Double decisionValue = compressedTree[actualNodeIndex][2];
        if (decisionValue != null) {
            return decisionValue.intValue();
        }

        if (rowValues.get(feature.intValue()) <= threshold) {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 1);
        } else {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 2);
        }
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
            List<Double> rowValues = new ArrayList<>();
            for (Chunk c : cs) {
                rowValues.add(c.atd(row));
            }
            System.out.println(nc.length + " len " + nc[0].len());
            nc[0].addNum(predictRowStartingFromNode(rowValues, 0));
        }
    }
}
