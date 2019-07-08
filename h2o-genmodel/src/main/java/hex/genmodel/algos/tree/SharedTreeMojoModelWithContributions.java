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
        SharedTreeGraph graph = _computeGraph(-1);
        final SharedTreeNode[] empty = new SharedTreeNode[0];
        List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(graph.subgraphArray.size());
        for (SharedTreeSubgraph tree : graph.subgraphArray) {
            SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
            treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
        TreeSHAPPredictor<double[]> predictor = new TreeSHAPEnsemble<>(treeSHAPs, (float) getInitF());
        
        return getContributionsPredictor(predictor);
    }

    public double getInitF() {
        return 0; // Set to zero by default, which is correct for DRF. However, need to override in GBMMojoModel with correct init_f.
    }
    
    protected abstract ContributionsPredictor getContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor);
    
    public abstract class ContributionsPredictor implements PredictContributions {
        private final TreeSHAPPredictor<double[]> _treeSHAPPredictor;
        private final Object _workspace;

        public ContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            _treeSHAPPredictor = treeSHAPPredictor;
            _workspace = _treeSHAPPredictor.makeWorkspace();
        }
        
        public float[] calculateContributions(double[] input) {
            float[] contribs = new float[nfeatures() + 1];
            _treeSHAPPredictor.calculateContributions(input, contribs, 0, -1, _workspace);
            return getContribs(contribs);
        }
        
        public float[] getContribs(float[] contribs) {
            return contribs;
        }
    }
}
