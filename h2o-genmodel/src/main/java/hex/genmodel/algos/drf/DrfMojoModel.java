package hex.genmodel.algos.drf;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.PredictContributions;
import hex.genmodel.PredictContributionsFactory;
import hex.genmodel.algos.tree.*;

import java.util.ArrayList;
import java.util.List;


/**
 * "Distributed Random Forest" MojoModel
 */
public final class DrfMojoModel extends SharedTreeMojoModel implements SharedTreeGraphConverter, PredictContributionsFactory {
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

    @Override
    public PredictContributions makeContributionsPredictor() {
        if (_nclasses > 2) {
            throw new UnsupportedOperationException("Predicting contributions for multinomial classification problems is not yet supported.");
        }
        SharedTreeGraph graph = _computeGraph(-1);
        final SharedTreeNode[] empty = new SharedTreeNode[0];
        List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(graph.subgraphArray.size());
        for (SharedTreeSubgraph tree : graph.subgraphArray) {
            SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
            treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
        TreeSHAPPredictor<double[]> predictor = new TreeSHAPEnsemble<>(treeSHAPs, 0);
        return new DrfMojoModel.DrfContributionsPredictor(predictor);
    }

    private final class DrfContributionsPredictor implements PredictContributions {
        private final TreeSHAPPredictor<double[]> _treeSHAPPredictor;
        private final Object _workspace;

        private DrfContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            _treeSHAPPredictor = treeSHAPPredictor;
            _workspace = _treeSHAPPredictor.makeWorkspace();
        }

        @Override
        public float[] calculateContributions(double[] input) {
            float[] contribs = new float[nfeatures() + 1];
            _treeSHAPPredictor.calculateContributions(input, contribs, 0, -1, _workspace);
            if (_category.equals(ModelCategory.Regression)) { // Regression
                for (int i = 0; i < contribs.length; i++) {
                    contribs[i] = contribs[i] / _ntree_groups;
                }
            } else { // Binomial
                float featurePlusBiasRatio = (float)1/(_nfeatures + 1);
                for (int i = 0; i < contribs.length; i++) {
                    contribs[i] = featurePlusBiasRatio - (contribs[i] / _ntree_groups);
                }
            }
            return contribs;
        }
    }

}
