package hex.genmodel.algos.isoforextended;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.ScoreIsolationTree;
import hex.genmodel.algos.tree.ScoreIsolationTree0;
import hex.genmodel.utils.ArrayUtils;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.MathUtils;

public final class ExtendedIsolationForestMojoModel extends MojoModel {

  int _ntrees;

  long _sample_size;

  byte[][] _compressedTrees;

  private ScoreIsolationTree _scoreIsolationTree;

  public ExtendedIsolationForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  public void postInit() {
    _scoreIsolationTree = new ScoreIsolationTree0();
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] preds) {
    double pathLength = 0;
    for(int treeId = 0; treeId < _ntrees; treeId++) {
      double iTreeScore = _scoreIsolationTree.scoreTree(_compressedTrees[treeId], row);
      pathLength += iTreeScore;
    }
    pathLength = pathLength / _ntrees;
    double anomalyScore = anomalyScore(pathLength, _sample_size);
    preds[0] = anomalyScore;
    preds[1] = pathLength;
    return preds;
  }

  @Override
  public int getPredsSize() {
    return 2;
  }

  @Override
  public String[] getOutputNames() {
      return new String[]{"anomaly_score", "mean_length"};
  }

  public static double scoreTree0(byte[] isolationTree, double[] row) {
    ByteBufferWrapper ab = new ByteBufferWrapper(isolationTree);
    int sizeOfBranchingArrays = ab.get4();
    double[] tempN = new double[sizeOfBranchingArrays];
    double[] tempP = new double[sizeOfBranchingArrays];
    int tempNodeNumber = 0;
    int tempNodeType = 0;
    int tempNumRows = 0;
    int height = 0;
    final int NODE = 'N';
    final int LEAF = 'L';
    int findNodeNumber = 0;
    double pathLength = -1;
    for(;;) {
      tempNodeNumber = ab.get4();
      tempNodeType = ab.get1U();
      if (tempNodeNumber != findNodeNumber) {
        if (tempNodeType == NODE) {
          ab.skip(2*sizeOfBranchingArrays*8);
        } else if (tempNodeType == LEAF) {
          ab.skip(4);
        } else {
          throw new UnsupportedOperationException("Unknown node type: " + tempNodeType);
        }
        continue;
      }
      if (tempNodeType == NODE) {
        loadNode(ab, tempN, tempP);
        double mul = ArrayUtils.subAndMul(row, tempP, tempN);
        if (mul <= 0) {
          // go left
          height++;
          findNodeNumber = leftChildIndex(tempNodeNumber);
        } else {
          height++;
          findNodeNumber = rightChildIndex(tempNodeNumber);
        }
      } else if (tempNodeType == LEAF) {
        tempNumRows = ab.get4();
        pathLength = height + averagePathLengthOfUnsuccessfulSearch(tempNumRows);
        break;
      } else {
        throw new UnsupportedOperationException("Unknown node type: " + tempNodeType);
      }
    }
    return pathLength;
  }

  private static void loadNode(ByteBufferWrapper ab, double[] n, double[] p) {
    for (int i = 0; i < n.length; i++) {
      n[i] = ab.get8d();
    }
    for (int i = 0; i < n.length; i++) {
      p[i] = ab.get8d();
    }
  }

  private static int leftChildIndex(int i) {
    return 2 * i + 1;
  }

  private static int rightChildIndex(int i) {
    return 2 * i + 2;
  }

  private static double anomalyScore(double pathLength, long sample_size) {
    return Math.pow(2, -1 * (pathLength /
            averagePathLengthOfUnsuccessfulSearch(sample_size)));
  }

  /**
   * Gives the average path length of unsuccessful search in BST.
   * Comes from Algorithm 3 (pathLength) and Equation 2 in paper
   *
   * @param n number of elements
   */
  public static double averagePathLengthOfUnsuccessfulSearch(long n) {
    if (n < 2)
      return 0;
    if (n == 2)
      return 1;
    return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
  }

}
