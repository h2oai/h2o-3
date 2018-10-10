package hex.genmodel.algos.tree;

public final class ScoreTree1 implements ScoreTree {

  @Override
  public final double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains) {
    return SharedTreeMojoModel.scoreTree1(tree, row, computeLeafAssignment);
  }

}
