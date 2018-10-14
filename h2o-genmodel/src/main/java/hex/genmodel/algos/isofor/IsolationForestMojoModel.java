package hex.genmodel.algos.isofor;

import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;

public final class IsolationForestMojoModel extends SharedTreeMojoModel {

  int _min_path_length;
  int _max_path_length;

  public IsolationForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] preds) {
    super.scoreAllTrees(row, preds);
    return unifyPreds(row, offset, preds);
  }

  @Override
  public double[] unifyPreds(double[] row, double offset, double[] preds) {
    if (_ntree_groups >= 1 && preds.length > 1) {
      preds[1] = preds[0] / _ntree_groups;
    }
    preds[0] = _max_path_length > _min_path_length ?
            (_max_path_length - preds[0]) / (_max_path_length - _min_path_length) : 1;
    return preds;
  }


}
