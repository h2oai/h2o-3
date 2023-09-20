package hex.genmodel.algos.upliftdrf;

import hex.ModelCategory;
import hex.genmodel.algos.tree.SharedTreeMojoModel;

public class UpliftDrfMojoModel extends SharedTreeMojoModel {
    
    protected double[] _thresholds;
    
    public UpliftDrfMojoModel(String[] columns, String[][] domains, String responseColumn, String treatmentColumn){
        super(columns, domains, responseColumn, treatmentColumn);
    }

    @Override
    public double[] unifyPreds(double[] row, double offset, double[] preds) {
        assert _nclasses == 2;
        preds[1] /= _ntree_groups;
        preds[2] /= _ntree_groups;
        preds[0] = preds[1] - preds[2];
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

    public double[] getThresholds() {
        return _thresholds;
    }

    @Override
    public int getPredsSize() {
        return 3;
    }

    @Override
    public int getPredsSize(ModelCategory mc) {
        return getPredsSize();
    }
}
