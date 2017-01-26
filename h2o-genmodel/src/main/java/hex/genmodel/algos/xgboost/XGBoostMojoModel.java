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

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }
  @Override
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, 1.0, preds);
  }
  public final double[] score0(double[] doubles, double offset, double weight, double[] preds) {
    assert(doubles != null) : "doubles are null";
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    if (_nums > 0) {
      floats = new float[_nums + cats]; //TODO: use thread-local storage
      GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels);
    } else {
      floats = new float[doubles.length];
      for (int i=0; i<floats.length; ++i) {
        floats[i] = (float) doubles[i];
      }
    }
    float[] predFloats = new float[_nclasses];
    try {
      float[][] out;
      DMatrix dmat = new DMatrix(floats,1,floats.length);
      dmat.setWeight(new float[]{(float)weight});
      out = _booster.predict(dmat);
      for (int i = 0; i < predFloats.length; ++i)
        predFloats[i] = out[i][0];
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }

    assert(_nclasses == predFloats.length) : "nclasses " + _nclasses + " predFloats.length " + predFloats.length;
    if (_nclasses > 1) {
      for (int i = 0; i < predFloats.length; ++i)
        preds[1 + i] = predFloats[i];
      if (_balanceClasses)
        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else {
      preds[0] = predFloats[0];
    }
    return preds;
  }

}
