package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.util.FVec;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.genmodel.algos.tree.TreeSHAPPredictor;

public class TreeSHAPHelper {

  public static TreeSHAPPredictor<FVec> makePredictor(RegTree tree) {
    RegTreeImpl treeImpl = (RegTreeImpl) tree;
    return new TreeSHAP<>(treeImpl.getNodes(), treeImpl.getStats(), 0);
  }

}
