package hex.genmodel.algos;

import hex.genmodel.GenModel;

import java.util.Map;

/**
 * "Distributed Random Forest" MojoModel
 */
public final class DrfModel extends TreeBasedModel {
    private int _effective_n_classes;
    private boolean _binomial_double_trees;


    public DrfModel(MojoReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _binomial_double_trees = (boolean) info.get("binomial_double_trees");
        _effective_n_classes = _nclasses == 2 && !_binomial_double_trees? 1 : _nclasses;
    }

    /**
     * Corresponds to `hex.tree.drf.DrfModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        super.scoreAllTrees(row, preds, _effective_n_classes);

        // Correct the predictions -- see `DRFModel.toJavaUnifyPreds`
        if (_nclasses == 1) {
            // Regression
            preds[0] /= _ntrees;
        } else {
            // Classification
            if (_nclasses == 2 && !_binomial_double_trees) {
                // Binomial model
                preds[1] /= _ntrees;
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
