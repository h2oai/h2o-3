package hex.genmodel.algos.xgboost;

import hex.genmodel.MojoModel;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class XGBoostMojoModel extends MojoModel {
  Booster _booster;

  public XGBoostMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public final double[] score0(double[] row, double offset, double[] preds) {

    float[] fdata = new float[row.length];
    for (int i=0;i<row.length;++i) fdata[i] = (float)row[i];
    DMatrix data = null;
    try {
      data = new DMatrix(fdata, 1, row.length);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
    //FIXME: Need to use DataInfo for one-hot encoding
    try {
      float[][] p = _booster.predict(data);
      if (_nclasses==1)
        preds[0] = p[0][0]; //FIXME
      else throw new IllegalArgumentException();
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
    return null;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

}
