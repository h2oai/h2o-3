package hex.genmodel.algos.tree;

import hex.genmodel.algos.isoforfaircut.FairCutForestMojoModel;

public final class ScoreIsolationTree1 implements ScoreIsolationTree {

    @Override
    public double scoreTree(byte[] tree, double[] row) {
        return FairCutForestMojoModel.scoreTree0(tree, row);
    }

}
