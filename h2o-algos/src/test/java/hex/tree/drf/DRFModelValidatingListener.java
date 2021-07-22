package hex.tree.drf;

import hex.Model;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.CompressedTree;
import hex.tree.SharedTreeModel;
import org.junit.Ignore;
import water.H2OListenerExtension;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.HashMap;
import java.util.Map;

import static hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;
import static org.junit.Assert.assertNotNull;

@Ignore
public class DRFModelValidatingListener implements H2OListenerExtension {

    @Override
    public String getName() {
        return "DRFModelValidatingListener";
    }

    @Override
    public void init() {
        // nothing to do
    }

    @Override
    public void report(String ctrl, Object... data) {
        assert "model_completed".equals(ctrl);
        assert data.length == 2;
        Model<?, ?, ?> model = (Model<?, ?, ?>) data[0];
        Model.Parameters parms = (Model.Parameters) data[1];
        if (!parms._is_cv_model && model instanceof DRFModel) {
            validateDRFModel((DRFModel) model, (DRFModel.DRFParameters) parms);
        }
    }

    void validateDRFModel(DRFModel model, DRFModel.DRFParameters parms) {
        HistogramType histoType = parms._histogram_type == HistogramType.AUTO ? 
                HistogramType.UniformAdaptive : parms._histogram_type; 
        if (histoType != HistogramType.UniformAdaptive) {
            Log.warn("Not validating models with Histogram type = " + parms._histogram_type);
            return;
        }
        Frame train = parms._train.get();
        Key<Frame> resultKey = Key.make();
        try {
            Frame nodeIds = model.scoreLeafNodeAssignment(
                    train, Model.LeafNodeAssignment.LeafNodeAssignmentType.Node_ID, resultKey);
            for (int treeNum = 0; treeNum < model._output._treeKeys.length; treeNum++) {
                Key<CompressedTree>[] treeKeys = model._output._treeKeys[treeNum];
                for (int treeClass = 0; treeClass < treeKeys.length; treeClass++) {
                    if (treeKeys[treeClass] != null) {
                        SharedTreeSubgraph tree = model.getSharedTreeSubgraph(treeNum, treeClass);
                        validateWeights(tree, nodeIds.vec(getNodeIdVecName(model, treeNum, treeClass)), train.vec(parms._weights_column));
                    }
                }
            }
        } finally {
            Frame f = resultKey.get();
            if (f != null)
                f.remove();
        }
    }

    String getNodeIdVecName(DRFModel m, int treeNum, int treeClass) {
        return m._output.isClassifier() ? "T" + (treeNum + 1) + ".C" + (treeClass + 1) : "T" + (treeNum + 1);
    }
    
    void validateWeights(SharedTreeSubgraph tree, Vec nodeIds, Vec weights) {
        Vec.Reader nodeIdReader = nodeIds.new Reader();
        Vec.Reader weightsReader = weights != null ? weights.new Reader() : null;
        Map<Integer, Double> nodeWeights = new HashMap<>();
        for (long l = 0; l < nodeIds.length(); l++) {
            Integer nodeId = (int) nodeIdReader.at8(l);
            double weight = weightsReader != null ? weightsReader.at(l) : 1d;
            if (!nodeWeights.containsKey(nodeId)) {
                nodeWeights.put(nodeId, 0.0d);
            }
            nodeWeights.put(nodeId, nodeWeights.get(nodeId) + weight);
        }
        for (SharedTreeNode node : tree.nodesArray) {
            if (!node.isLeaf() 
                    || node.getDepth() > 63) // deep trees will have NAs/-1 nodeIds, we cannot check them
                continue;
            Double expectedWeight = nodeWeights.get(node.getNodeNumber());
            assertNotNull("Node " + node.getDebugId() + ") should have some observations", expectedWeight);
        }
    }
    
}
