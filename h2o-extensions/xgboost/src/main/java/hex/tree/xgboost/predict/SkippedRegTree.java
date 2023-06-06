package hex.tree.xgboost.predict;

import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.tree.RegTreeNodeStat;
import biz.k11i.xgboost.util.FVec;
import biz.k11i.xgboost.util.ModelReader;

import java.io.IOException;

public class SkippedRegTree implements RegTree {

   SkippedRegTree(ModelReader reader) throws IOException {
        final int numNodes = XGBoostRegTree.readNumNodes(reader);
        reader.skip((long) numNodes * XGBoostRegTree.NODE_SIZE);
        reader.skip((long) numNodes * XGBoostRegTree.STATS_SIZE);
    }

    @Override
    public int getLeafIndex(FVec feat) {
        return 0;
    }

    @Override
    public void getLeafPath(FVec feat, StringBuilder sb) {
       // nothing to do
    }

    @Override
    public float getLeafValue(FVec feat, int root_id) {
        return 0;
    }

    @Override
    public RegTreeNode[] getNodes() {
        return new RegTreeNode[0];
    }

    @Override
    public RegTreeNodeStat[] getStats() {
        return new RegTreeNodeStat[0];
    }

}
