package hex.genmodel.algos.tree;

public final class ScoreTree2 implements ScoreTree {

  @Override
  public final double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains) {
    return SharedTreeMojoModel.scoreTree(tree, row, computeLeafAssignment, domains);
  }

}
