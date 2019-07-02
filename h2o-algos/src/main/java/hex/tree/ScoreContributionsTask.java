package hex.tree;

import hex.genmodel.algos.tree.*;
import water.Key;
import water.MRTask;
import water.MemoryManager;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.ArrayList;
import java.util.List;

public class ScoreContributionsTask extends MRTask {
    
    private final Key<SharedTreeModel> _modelKey;
    
    private transient SharedTreeModel _model;
    private transient int _ntrees;
    private transient Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    private transient double _init_f;
    private transient TreeSHAPPredictor<double[]> _treeSHAP;

    public ScoreContributionsTask(SharedTreeModel model, int ntrees, 
                                  Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] treeKeys, 
                                  double init_f) {
        _modelKey = model._key;
        _ntrees = ntrees;
        _treeKeys = treeKeys;
        _init_f = init_f;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setupLocal() {
        _model = _modelKey.get();
        assert _model != null;
        final SharedTreeNode[] empty = new SharedTreeNode[0];
        List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(_ntrees);
        for (int treeIdx = 0; treeIdx < _ntrees; treeIdx++) {
            for (int treeClass = 0; treeClass < _treeKeys[treeIdx].length; treeClass++) {
                if (_treeKeys[treeIdx][treeClass] == null) {
                    continue;
                }
                SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(treeIdx, treeClass);
                SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
                treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
            }
        }
        assert treeSHAPs.size() == _ntrees; // for now only regression and binomial to keep the output sane
        _treeSHAP = new TreeSHAPEnsemble<>(treeSHAPs, (float) _init_f);
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
        assert chks.length == nc.length - 1; // calculate contribution for each feature + the model bias
        double[] input = MemoryManager.malloc8d(chks.length);
        float[] contribs = MemoryManager.malloc4f(nc.length);

        Object workspace = _treeSHAP.makeWorkspace();

        for (int row = 0; row < chks[0]._len; row++) {
            for (int i = 0; i < chks.length; i++) {
                input[i] = chks[i].atd(row);
            }
            for (int i = 0; i < contribs.length; i++) {
                contribs[i] = 0;
            }

            // calculate Shapley values
            _treeSHAP.calculateContributions(input, contribs, 0, -1, workspace);
            
            if (_model._parms.algoName().equals("GBM")) { // GBM
                for (int i = 0; i < nc.length; i++) {
                    nc[i].addNum(contribs[i]);
                }
            } else if (_model._parms.algoName().equals("DRF"))  { // DRF
                for (int i = 0; i < nc.length; i++) {
                    // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
                    if (_model._output.nclasses() == 1) { //Regression
                        nc[i].addNum(contribs[i] / _ntrees);
                    } else { //Binomial
                        float featurePlusBiasRatio = (float)1 / (_model._output.nfeatures() + 1); // + 1 for bias term
                        nc[i].addNum(featurePlusBiasRatio - (contribs[i] / _ntrees));
                    }
                }
            } else {
                throw new H2OIllegalArgumentException("Calculating prediction contributions are not supported for " + _model._parms.algoName());
            }
        }
    }
}
