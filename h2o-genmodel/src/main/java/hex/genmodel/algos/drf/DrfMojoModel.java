package hex.genmodel.algos.drf;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.PredictContributions;
import hex.genmodel.algos.tree.*;
import hex.genmodel.attributes.VariableImportances;
import hex.genmodel.attributes.parameters.VariableImportancesHolder;


/**
 * "Distributed Random Forest" MojoModel
 */
public final class DrfMojoModel extends SharedTreeMojoModelWithContributions implements SharedTreeGraphConverter {
    protected boolean _binomial_double_trees;


    public DrfMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    @Override
    protected PredictContributions getContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor) {
        return new ContributionsPredictorDRF(this, treeSHAPPredictor);
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
    
    static class ContributionsPredictorDRF extends SharedTreeContributionsPredictor {

        private final float _featurePlusBiasRatio;
        private final int _normalizer;
        
        private ContributionsPredictorDRF(DrfMojoModel model, TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            super(model, treeSHAPPredictor);
            if (model._binomial_double_trees) {
                throw new UnsupportedOperationException(
                        "Calculating contributions is currently not supported for model with binomial_double_trees parameter set.");
            }
            int numberOfUsedVariables = ((VariableImportancesHolder) model._modelAttributes).getVariableImportances().numberOfUsedVariables();
            if (ModelCategory.Regression.equals(model._category)) {
                _featurePlusBiasRatio = 0;
                _normalizer = model._ntree_groups;
            } else if (ModelCategory.Binomial.equals(model._category)) {
                _featurePlusBiasRatio = 1f / (numberOfUsedVariables + 1);
                _normalizer = -model._ntree_groups;
            } else 
                throw new UnsupportedOperationException(
                        "Model category " + model._category + " cannot be used to calculate feature contributions.");
        }

        @Override
        public float[] getContribs(float[] contribs) {
            for (int i = 0; i < contribs.length; i++) {
                if (contribs[i] != 0)
                    contribs[i] = _featurePlusBiasRatio + (contribs[i] / _normalizer);
            }
            return contribs;    
        }
    }

    public boolean isBinomialDoubleTrees() {
        return _binomial_double_trees;
    }
}
