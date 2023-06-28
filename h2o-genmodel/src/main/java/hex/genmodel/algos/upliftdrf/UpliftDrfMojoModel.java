package hex.genmodel.algos.upliftdrf;

import hex.genmodel.algos.tree.SharedTreeMojoModel;

public class UpliftDrfMojoModel extends SharedTreeMojoModel {
    
    public UpliftDrfMojoModel(String[] columns, String[][] domains, String responseColumn, String treatmentColumn){
        super(columns, domains, responseColumn, treatmentColumn);
    }

    @Override
    public double[] unifyPreds(double[] row, double offset, double[] preds) {
        assert _nclasses == 2;
        preds[0] /= _ntree_groups;
        preds[1] /= _ntree_groups;
        preds[2] /= _ntree_groups;
        return preds;
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        super.scoreAllTrees(row, preds);
        return unifyPreds(row, 0, preds);
    }
    
    @Override
    public double getInitF() {
        return 0;
    }
}
