package hex.genmodel.algos.tree;

public final class ScoreTree2 implements ScoreTree {

  @Override
  public final double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment, String[][] domains) {
    return SharedTreeMojoModel.scoreTree(tree, row, nclasses, computeLeafAssignment, domains);
  }

}
