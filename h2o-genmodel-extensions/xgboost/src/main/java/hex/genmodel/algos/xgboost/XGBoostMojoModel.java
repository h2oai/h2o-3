package hex.genmodel.algos.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;

import java.io.Closeable;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public abstract class XGBoostMojoModel extends MojoModel implements Closeable {

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;
  public boolean _sparse;
  public String _featureMap;

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public final double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  // for float output
  public static double[] toPreds(double in[], float[] out, double[] preds,
                          int nclasses, double[] priorClassDistrib, double defaultThreshold) {
    if (nclasses > 2) {
      for (int i = 0; i < out.length; ++i)
        preds[1 + i] = out[i];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else if (nclasses==2){
      preds[1] = 1 - (double) out[0];
      preds[2] = out[0];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else {
      preds[0] = out[0];
    }
    return preds;
  }

}
