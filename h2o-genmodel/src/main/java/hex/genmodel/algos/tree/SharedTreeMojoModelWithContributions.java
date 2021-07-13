package hex.genmodel.algos.tree;

import hex.genmodel.PredictContributions;
import hex.genmodel.PredictContributionsFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class SharedTreeMojoModelWithContributions extends SharedTreeMojoModel implements PredictContributionsFactory {
    protected SharedTreeMojoModelWithContributions(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    public PredictContributions makeContributionsPredictor() {
        if (_nclasses > 2) {
            throw new UnsupportedOperationException("Predicting contributions for multinomial classification problems is not yet supported.");
        }
        SharedTreeGraph graph = computeGraph(-1);
        List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(graph.subgraphArray.size());
        for (SharedTreeSubgraph tree : graph.subgraphArray) {
            SharedTreeNode[] nodes = tree.getNodes();
            treeSHAPs.add(new TreeSHAP<>(nodes));
        }
        TreeSHAPPredictor<double[]> predictor = new TreeSHAPEnsemble<>(treeSHAPs, (float) getInitF());
        
        return getContributionsPredictor(predictor);
    }

    public double getInitF() {
        return 0; // Set to zero by default, which is correct for DRF. However, need to override in GBMMojoModel with correct init_f.
    }
    
    protected abstract PredictContributions getContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor);

    protected static class SharedTreeContributionsPredictor extends ContributionsPredictor<double[]> {

        public SharedTreeContributionsPredictor(SharedTreeMojoModel model, TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            super(model._nfeatures + 1, model.features(), treeSHAPPredictor);
        }

        @Override
        protected final double[] toInputRow(double[] input) {
            return input;
        }
    }
}
