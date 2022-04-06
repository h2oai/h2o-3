package hex.genmodel.algos.tree;

import java.io.Serializable;

public interface ScoreIsolationTree extends Serializable {

  double scoreTree(byte[] tree, double[] row);

}
