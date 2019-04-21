package hex.genmodel.algos.drf;

import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;


/**
 * "Distributed Random Forest" MojoModel
 */
public final class DrfMojoModel extends SharedTreeMojoModel implements SharedTreeGraphConverter {
    protected boolean _binomial_double_trees;


    public DrfMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    /**
     * Corresponds to `hex.tree.drf.DrfMojoModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        super.scoreAllTrees(row, preds);
        return unifyPreds(row, offset, preds);
    }


    @Override
    public final double[] unifyPreds(double[] row, double offset, double[] preds) {
        // Correct the predictions -- see `DRFModel.toJavaUnifyPreds`
        if (_nclasses == 1) {
            // Regression
            preds[0] /= _ntree_groups;
        } else {
            // Classification
            if (_nclasses == 2 && !_binomial_double_trees) {
                // Binomial model
                preds[1] /= _ntree_groups;
                preds[2] = 1.0 - preds[1];
            } else {
                // Multinomial
                double sum = 0;
                for (int i = 1; i <= _nclasses; i++) { sum += preds[i]; }
                if (sum > 0)
                    for (int i = 1; i <= _nclasses; i++) { preds[i] /= sum; }
            }
            if (_balanceClasses)
                GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
            preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, row, _defaultThreshold);
        }
        return preds;
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        return score0(row, 0.0, preds);
    }

}
