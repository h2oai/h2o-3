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
    
    protected static class ContributionsPredictor implements PredictContributions {
        private final int _nfeatures;
        private final TreeSHAPPredictor<double[]> _treeSHAPPredictor;

        private static ThreadLocal<Object> _workspace = new ThreadLocal<>();
        
        public ContributionsPredictor(SharedTreeMojoModel model, TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            _nfeatures = model._nfeatures;
            _treeSHAPPredictor = treeSHAPPredictor;
        }
        
        public final float[] calculateContributions(double[] input) {
            float[] contribs = new float[_nfeatures + 1];
            _treeSHAPPredictor.calculateContributions(input, contribs, 0, -1, getWorkspace());
            return getContribs(contribs);
        }
        
        private Object getWorkspace() {
            Object workspace = _workspace.get();
            if (workspace == null) {
                workspace = _treeSHAPPredictor.makeWorkspace();
                _workspace.set(workspace);
            }
            return workspace;
        }
        
        public float[] getContribs(float[] contribs) {
            return contribs;
        }
    }
}
