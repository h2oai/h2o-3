package hex.api;

import hex.schemas.CompressedTreesModelV3;
import hex.schemas.TreeVizSchemaV3;
import hex.tree.CompressedTree;
import hex.tree.SharedTreeModel;
import hex.tree.TreeStats;
import hex.tree.gbm.GBMModel;
import water.*;
import water.api.Handler;

//Created by Manu Srimat

public class TreeVizHandler extends Handler {

  public TreeVizSchemaV3 get_trees(int version, TreeVizSchemaV3 args){
    // get the model by key, fetch the trees set them to schema
    GBMModel m = DKV.getGet(args.modelKey.key());

    args.trees = new CompressedTreesModelV3(m,0,m._output._treeKeys.length);
    return args;
  }
}
