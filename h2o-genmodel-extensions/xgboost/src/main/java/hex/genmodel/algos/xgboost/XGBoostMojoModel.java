package hex.genmodel.algos.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.HashMap;
import java.util.Map;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class XGBoostMojoModel extends MojoModel {
  Booster _booster;

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;
  public boolean _sparse;

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn,
                          Booster _booster, int _nums, int _cats, int[] _catOffsets, boolean _useAllFactorLevels, boolean _sparse) {
    super(columns, domains, responseColumn);
    this._booster = _booster;
    this._nums = _nums;
    this._cats = _cats;
    this._catOffsets = _catOffsets;
    this._useAllFactorLevels = _useAllFactorLevels;
    this._sparse = _sparse;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, preds,
            _booster, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold, _sparse);
  }

  public static double[][] bulkScore0(double[][] doubles, double[] offsets, double[][] preds,
                                      Booster _booster, int _nums, int _cats,
                                      int[] _catOffsets, boolean _useAllFactorLevels,
                                      int nclasses, double[] _priorClassDistrib,
                                      double _defaultThreshold, boolean _sparse) {
    if (offsets != null) throw new UnsupportedOperationException("Unsupported: offset != null or only 0s");
    float[][] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[doubles.length][_nums + cats]; //TODO: use thread-local storage
    for(int i = 0; i < doubles.length; i++) {
      GenModel.setInput(doubles[i], floats[i], _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    }
    float[][] out = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      DMatrix dmat = new DMatrix(floats,doubles.length,floats[0].length, _sparse ? 0 : Float.NaN);
//      dmat.setWeight(new float[]{(float)weight});
      out = _booster.predict(dmat);
      Rabit.shutdown();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    }

    for(int r = 0; r < out.length; r++) {
      if (nclasses > 2) {
        for (int i = 0; i < out[0].length; ++i)
          preds[r][1 + i] = out[r][i];
//      if (_balanceClasses)
//        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[r][0] = GenModel.getPrediction(preds[r], _priorClassDistrib, doubles[r], _defaultThreshold);
      } else if (nclasses == 2) {
        preds[r][1] = 1 - out[r][0];
        preds[r][2] = out[r][0];
        preds[r][0] = GenModel.getPrediction(preds[r], _priorClassDistrib, doubles[r], _defaultThreshold);
      } else {
        preds[r][0] = out[r][0];
      }
    }
    return preds;
  }

  public static double[] score0(double[] doubles, double offset, double[] preds,
                                Booster _booster, int _nums, int _cats,
                                int[] _catOffsets, boolean _useAllFactorLevels,
                                int nclasses, double[] _priorClassDistrib,
                                double _defaultThreshold, boolean _sparse) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    float[][] out = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      DMatrix dmat = new DMatrix(floats,1,floats.length, _sparse ? 0 : Float.NaN);
//      dmat.setWeight(new float[]{(float)weight});
      out = _booster.predict(dmat);
      Rabit.shutdown();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    }

    if (nclasses > 2) {
      for (int i = 0; i < out[0].length; ++i)
        preds[1 + i] = out[0][i];
//      if (_balanceClasses)
//        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else if (nclasses==2){
      preds[1] = 1 - out[0][0];
      preds[2] = out[0][0];
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else {
      preds[0] = out[0][0];
    }
    return preds;
  }

}
