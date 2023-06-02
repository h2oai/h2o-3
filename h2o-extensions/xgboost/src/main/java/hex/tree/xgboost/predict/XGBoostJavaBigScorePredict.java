package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.DataInfo;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostModelInfo;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class XGBoostJavaBigScorePredict implements XGBoostBigScorePredict {

  private final DataInfo _di;
  private final XGBoostOutput _output;
  private final XGBoostModel.XGBoostParameters _parms;
  private final double _threshold;
  private final Predictor _predictor;
  private final boolean[] _usedColumns;

  public XGBoostJavaBigScorePredict(
      XGBoostModelInfo model_info,
      XGBoostOutput output,
      DataInfo di,
      XGBoostModel.XGBoostParameters parms, double threshold
  ) {
    _di = di;
    _output = output;
    _parms = parms;
    _threshold = threshold;
    _predictor = PredictorFactory.makePredictor(model_info._boosterBytes);
    _usedColumns = findUsedColumns(_predictor.getBooster(), di, _output.nfeatures());
  }

  @Override
  public XGBoostPredict initMap(Frame fr, Chunk[] chks) {
    return new XGBoostJavaBigScoreChunkPredict(_di, _output, _parms, _threshold, _predictor, _usedColumns, fr);
  }

  /**
   * For each input feature decides whether it is used by the model or not.
   * 
   * @param booster booster
   * @param di data info
   * @param nFeatures number of features provided at training time
   * @return boolean flag for each input feature indicating whether it is used or not, returns null if the used
   * features cannot be determined or model uses all of them (null thus effectively means use everything to the caller)
   */
  static boolean[] findUsedColumns(GradBooster booster, DataInfo di, final int nFeatures) {
    if (! (booster instanceof GBTree)) {
      return null;
    }
    int[] splitIndexToColumnIndex = di.coefOriginalColumnIndices();
    assert ArrayUtils.maxValue(splitIndexToColumnIndex) < nFeatures; // this holds because feature columns go first, before special columns
    boolean[] usedColumns = new boolean[nFeatures];
    int usedCount = 0;
    for (RegTree[] trees : ((GBTree) booster).getGroupedTrees()) {
      for (RegTree tree : trees) {
        for (RegTreeNode node : tree.getNodes()) {
          if (node.isLeaf()) {
            continue;
          }
          int column = splitIndexToColumnIndex[node.getSplitIndex()];
          if (!usedColumns[column]) {
            usedCount++;
            usedColumns[column] = true;
            if (splitIndexToColumnIndex.length == usedCount) { // all columns already used, abort
              return null;
            }
          }
        }
      }
    }
    return usedColumns;
  }

}
