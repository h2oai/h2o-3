package hex.schemas;

import hex.api.TreeVizHandler;
import hex.tree.CompressedTree;
import hex.tree.SharedTreeModel;
import water.AutoBuffer;
import water.DKV;
import water.api.schemas3.SchemaV3;

import java.util.ArrayList;
import java.util.List;

public class CompressedTreesModelV3 extends SchemaV3<CompressedTree, CompressedTreesModelV3> {

    int _treesFrom;
    int _treesTo;
    transient SharedTreeModel _m;

    public CompressedTreesModelV3() {}

    public CompressedTreesModelV3(SharedTreeModel m, int treesFrom, int treesTo) {
        _m = m;
        _treesFrom = treesFrom;
        _treesTo = treesTo;
    }


    public final static AutoBuffer writeJSON_impl(CompressedTreesModelV3 tm, AutoBuffer ab) {
        SharedTreeModel.SharedTreeOutput output = (SharedTreeModel.SharedTreeOutput) tm._m._output;
        int k = 0;
        ab.put1('[');
        for (int i = tm._treesFrom; i < tm._treesTo; ++i) {
            for (int j = 0; j < output._treeKeys[i].length; ++j) {
                CompressedTree ct = DKV.getGet(output._treeKeys[i][j]);
                byte[] json = ct.toJSON(output).getBytes();
                if (k++ > 0) ab.put1(',');
                ab.putA1(json, json.length);
            }
        }
        ab.put1(']');
        return ab;
    }

    @Override
    public AutoBuffer writeJSON(AutoBuffer ab) {
        return writeJSON_impl(this, ab);
    }
}
