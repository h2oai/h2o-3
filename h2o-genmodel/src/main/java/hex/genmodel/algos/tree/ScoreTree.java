package hex.genmodel.algos.tree;

import java.io.Serializable;

public interface ScoreTree extends Serializable {

  double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment, String[][] domains);

}
