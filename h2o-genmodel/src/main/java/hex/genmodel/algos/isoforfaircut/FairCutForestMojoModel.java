package hex.genmodel.algos.isoforfaircut;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.ScoreIsolationTree;
import hex.genmodel.algos.tree.ScoreIsolationTree1;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.MathUtils;

public final class FairCutForestMojoModel extends MojoModel {

  public static final int NODE = 'N';
  public static final int LEAF = 'L';

  int _ntrees;

  long _sample_size;
  
  byte[][] _compressedTrees;

  private ScoreIsolationTree _scoreIsolationTree;

  public FairCutForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  public void postInit() {
    _scoreIsolationTree = new ScoreIsolationTree1();
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
    double[] tempNormalVector = new double[sizeOfBranchingArrays];
    double[] tempIntercept = {0};
    double[] tempMeans = new double[sizeOfBranchingArrays];
    double[] tempStds = new double[sizeOfBranchingArrays];
    int tempNodeNumber, tempNodeType, tempNumRows, height = 0, findNodeNumber = 0;
    final int SIZE_OF_NODE = 3*sizeOfBranchingArrays*8 + 8; // normal vector, stds, means and intercept
    final int SIZE_OF_LEAF = 4; // number of rows
    double pathLength = -1;

    while(ab.hasRemaining()) {
      tempNodeNumber = ab.get4();
      tempNodeType = ab.get1U();
      if (tempNodeNumber != findNodeNumber) {
        if (tempNodeType == NODE) {
          ab.skip(SIZE_OF_NODE);
        } else if (tempNodeType == LEAF) {
          ab.skip(SIZE_OF_LEAF);
        } else {
          throw new UnsupportedOperationException("Unknown node type: " + tempNodeType);
        }
        continue;
      }
      if (tempNodeType == NODE) {
        loadNode(ab, tempNormalVector, tempIntercept, tempMeans, tempStds);
        double z = 0;
        for (int i = 0; i < row.length; i++) {
          z += ((row[i] - tempMeans[i]) / tempStds[i]) * tempNormalVector[i];
        }
        if (z < tempIntercept[0]) {
          // go left
          findNodeNumber = leftChildIndex(tempNodeNumber);
        } else {
          // go right
          findNodeNumber = rightChildIndex(tempNodeNumber);
        }
        height++;
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

  private static void loadNode(ByteBufferWrapper ab, 
                               double[] normalVector, double[] intercept, double[] means, double[] stds) {
    for (int i = 0; i < normalVector.length; i++) {
      normalVector[i] = ab.get8d();
    }
    intercept[0] = ab.get8d();
    for (int i = 0; i < normalVector.length; i++) {
      means[i] = ab.get8d();
    }
    for (int i = 0; i < normalVector.length; i++) {
      stds[i] = ab.get8d();
    }
  }

  public static int leftChildIndex(int i) {
    return 2 * i + 1;
  }

  public static int rightChildIndex(int i) {
    return 2 * i + 2;
  }

  /**
   * Anomaly score computation comes from Equation 1 in paper
   *
   * @param pathLength path from root to leaf
   * @return anomaly score in range [0, 1]
   */
  public static double anomalyScore(double pathLength, long sample_size) {
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
