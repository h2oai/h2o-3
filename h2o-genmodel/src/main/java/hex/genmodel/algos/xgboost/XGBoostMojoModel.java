package hex.genmodel.algos.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class XGBoostMojoModel extends MojoModel {
  Booster _booster;

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;

  public XGBoostMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  public XGBoostMojoModel(String[] columns, String[][] domains, Booster _booster, int _nums, int _cats, int[] _catOffsets, boolean _useAllFactorLevels) {
    super(columns, domains);
    this._booster = _booster;
    this._nums = _nums;
    this._cats = _cats;
    this._catOffsets = _catOffsets;
    this._useAllFactorLevels = _useAllFactorLevels;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }
  @Override
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, 1.0, preds);
  }
  public final double[] score0(double[] doubles, double offset, double weight, double[] preds) {
    return score0(doubles, offset, weight, preds,
            _booster, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold);
  }
  public static final double[] score0(double[] doubles, double offset, double weight, double[] preds,
      Booster _booster, int _nums, int _cats, int[] _catOffsets, boolean _useAllFactorLevels,
                                      int nclasses, double[] _priorClassDistrib, double _defaultThreshold) {
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels);
    float[][] out = null;
    try {
      DMatrix dmat = new DMatrix(floats,1,floats.length);
      dmat.setWeight(new float[]{(float)weight});
      out = _booster.predict(dmat);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }

    if (nclasses > 2) {
      for (int i = 0; i < out.length; ++i)
        preds[1 + i] = out[i][0];
//      if (_balanceClasses)
//        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else if (nclasses==2){
      preds[0] = 1 - out[0][0];
      preds[1] = out[0][0];
    } else {
      preds[0] = out[0][0];
    }
    return preds;
  }

}
