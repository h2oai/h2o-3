package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

public class UpdateAuxTreeWeightsTask extends MRTask<UpdateAuxTreeWeightsTask> {

    // IN
    private final DistributionFamily _dist;
    private final Predictor _p;
    private final DataInfo _di;
    private final boolean _sparse;
    // OUT
    private final double[/*treeId*/][/*leafNodeId*/] _nodeWeights;

    public UpdateAuxTreeWeightsTask(DistributionFamily dist, DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output) {
        _dist = dist;
        _p = PredictorFactory.makePredictor(modelInfo._boosterBytes, null, false);
        _di = di;
        _sparse = output._sparse;

        if (_p.getNumClass() > 2) {
            throw new UnsupportedOperationException("Updating tree weights is currently not supported for multinomial models.");
        }
        if (_dist != DistributionFamily.gaussian && _dist != DistributionFamily.bernoulli) {
            throw new UnsupportedOperationException("Updating tree weights is currently not supported for distribution " + _dist + ".");
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
        LinkFunction logit = LinkFunctionFactory.getLinkFunction(LinkFunctionType.logit);
        RegTree[] trees = ((GBTree) _p.getBooster()).getGroupedTrees()[0];

        MutableOneHotEncoderFVec inputVec = new MutableOneHotEncoderFVec(_di, _sparse);
        int inputLength = chks.length - 1;
        int weightIndex = chks.length;
        double[] input = new double[inputLength];
        for (int row = 0; row < chks[0]._len; row++) {
            double weight = chks[weightIndex].atd(row);
            if (weight == 0 || Double.isNaN(weight))
                continue;
            for (int i = 0; i < input.length; i++)
                input[i] = chks[i].atd(row);
            inputVec.setInput(input);
            int[] leafIdx = _p.getBooster().predictLeaf(inputVec, _nodeWeights.length);
            assert leafIdx.length == _nodeWeights.length;
            if (_dist == DistributionFamily.gaussian) {
                for (int i = 0; i < leafIdx.length; i++) {
                    _nodeWeights[i][leafIdx[i]] += weight;
                }
            } else {
                assert _dist == DistributionFamily.bernoulli;
                double f = -_p.getBaseScore();
                for (int i = 0; i < leafIdx.length; i++) {
                    RegTreeNode[] nodes = trees[i].getNodes();
                    double p = logit.linkInv(f);
                    double hessian = p * (1 - p);
                    _nodeWeights[i][leafIdx[i]] += weight * hessian;
                    f += nodes[leafIdx[i]].getLeafValue();
                }
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
