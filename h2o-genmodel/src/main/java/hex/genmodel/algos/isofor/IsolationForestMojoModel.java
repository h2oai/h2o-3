package hex.genmodel.algos.isofor;

import hex.genmodel.algos.tree.SharedTreeMojoModel;

public final class IsolationForestMojoModel extends SharedTreeMojoModel {

  int _min_path_length;
  int _max_path_length;
  boolean _outputAnomalyFlag;

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
    double mpLength = 0;
    if (_ntree_groups >= 1 && preds.length > 1) {
      mpLength = preds[0] / _ntree_groups;
    }
    double score = _max_path_length > _min_path_length ? 
            (_max_path_length - preds[0]) / (_max_path_length - _min_path_length) : 1;
    if (_outputAnomalyFlag) {
      preds[0] = score > _defaultThreshold ? 1 : 0;
      preds[1] = score;
      preds[2] = mpLength;
    } else {
      preds[0] = score;
      preds[1] = mpLength;
    }
    return preds;
  }

  @Override
  public double getInitF() {
    return 0;
  }

  @Override
  public int getPredsSize() {
    return _outputAnomalyFlag ? 3 : 2;
  }

  @Override
  public String[] getOutputNames() {
    if (_outputAnomalyFlag) {
      return new String[]{"predict", "score", "mean_length"};
    } else {
      return new String[]{"predict", "mean_length"};
    }
  }

}
