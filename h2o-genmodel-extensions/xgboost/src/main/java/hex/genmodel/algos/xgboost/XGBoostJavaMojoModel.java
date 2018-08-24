package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.learner.ObjFunction;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of XGBoostMojoModel that uses Pure Java Predict
 * see https://github.com/h2oai/xgboost-predictor
 */
public final class XGBoostJavaMojoModel extends XGBoostMojoModel {

  Predictor _predictor;

  static {
    ObjFunction.register();
  }

  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
    _predictor = makePredictor(boosterBytes);
  }

  public static Predictor makePredictor(byte[] boosterBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return new Predictor(is);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, preds,
            _predictor, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold, _sparse);
  }

  public static double[] score0(double[] doubles, double offset, double[] preds,
                                Predictor predictor, int _nums, int _cats,
                                int[] _catOffsets, boolean _useAllFactorLevels,
                                int nclasses, double[] _priorClassDistrib,
                                double _defaultThreshold, boolean _sparse) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);

    FVec row = FVec.Transformer.fromArray(floats, _sparse);
    double[] out = predictor.predict(row);

    return toPreds(doubles, out, preds, nclasses, _priorClassDistrib, _defaultThreshold);
  }

  @Override
  public void close() {
    _predictor = null;
  }

  private static class RegObjFunction


}
