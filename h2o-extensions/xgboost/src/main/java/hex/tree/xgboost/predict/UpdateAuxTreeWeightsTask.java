package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.DataInfo;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

public class UpdateAuxTreeWeightsTask extends MRTask<UpdateAuxTreeWeightsTask> {

    // IN
    private final Predictor _p;
    private final DataInfo _di;
    private final boolean _sparse;
    // OUT
    private final double[/*treeId*/][/*leafNodeId*/] _nodeWeights;

    public UpdateAuxTreeWeightsTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output) {
        _p = PredictorFactory.makePredictor(modelInfo._boosterBytes, null, false);
        _di = di;
        _sparse = output._sparse;

        if (_p.getNumClass() > 2) {
            throw new UnsupportedOperationException("Calculating contributions is currently not supported for multinomial models.");
        }
        GBTree gbTree = (GBTree) _p.getBooster();
        RegTree[] trees = gbTree.getGroupedTrees()[0];
        _nodeWeights = new double[trees.length][];
        for (int i = 0; i < trees.length; i++) {
            _nodeWeights[i] = new double[trees[i].getStats().length];
        }
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] idx) {
        MutableOneHotEncoderFVec inputVec = new MutableOneHotEncoderFVec(_di, _sparse);
        double[] input = new double[chks.length - 1];
        for (int row = 0; row < chks[0]._len; row++) {
            double weight = chks[input.length].atd(row);
            if (weight == 0 || Double.isNaN(weight))
                continue;
            for (int i = 0; i < input.length; i++)
                input[i] = chks[i].atd(row);
            inputVec.setInput(input);
            int[] leafIdx = _p.getBooster().predictLeaf(inputVec, _nodeWeights.length);
            assert leafIdx.length == _nodeWeights.length;
            for (int i = 0; i < leafIdx.length; i++) {
                _nodeWeights[i][leafIdx[i]] += weight;
            }
        }
    }

    @Override
    public void reduce(UpdateAuxTreeWeightsTask mrt) {
        ArrayUtils.add(_nodeWeights, mrt._nodeWeights);
    }

    @Override
    protected void postGlobal() {
        GBTree gbTree = (GBTree) _p.getBooster();
        RegTree[] trees = gbTree.getGroupedTrees()[0];
        for (int i = 0; i < trees.length; i++) {
            RegTreeNode[] nodes = trees[i].getNodes();
            for (int j = nodes.length - 1; j >= 0; j--) {
                RegTreeNode node = nodes[j];
                int parentId = node.getParentIndex();
                if (parentId < 0)
                    continue;
                assert parentId < j;
                RegTreeNode parent = nodes[parentId];
                _nodeWeights[i][parentId] = _nodeWeights[i][parent.getLeftChildIndex()] + _nodeWeights[i][parent.getRightChildIndex()]; 
            }
        }
    }

    public double[][] getNodeWeights() {
        return _nodeWeights;
    }
}
