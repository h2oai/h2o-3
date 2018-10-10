package hex.genmodel.algos.tree;

import java.io.Serializable;

public interface ScoreTree extends Serializable {

  double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains);

}
