package hex.tree.xgboost;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;
import water.Key;
import water.util.TwoDimTable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


/**
 * This class contains the state of the Deep Learning model
 * This will be shared: one per node
 */
final public class XGBoostModelInfo extends Iced {
  private int _classes;
  byte[] _boosterBytes; // internal state of native backend

  private TwoDimTable summaryTable;

  transient Booster _booster;  //pointer to C++ process

  Key<DataInfo> _dataInfoKey;

  void nukeBackend() {
    if (_booster != null) {
      _booster.dispose();
    }
    _booster = null;
  }

  void javaToNative() {
    InputStream is = new ByteArrayInputStream(_boosterBytes);
    try {
      _booster = Booster.loadModel(is);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void nativeToJava() {
    try {
      _boosterBytes = _booster.toByteArray();
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
  }

  void saveNativeState(String path) {
    assert(_booster !=null);
    try {
      _booster.saveModel(path);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
  }

  float[] predict(float[] data) {
    try {
      DMatrix dmat = new DMatrix(data, 1, data.length);
      float[][] p = _booster.predict(dmat);
      float[] preds = new float[p.length];
      for (int j = 0; j < preds.length; ++j)
        preds[j] = p[j][0];
      return preds;
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
    return null;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(_boosterBytes);
  }

  // compute model size (number of model parameters required for making predictions)
  // momenta are not counted here, but they are needed for model building
  public long size() {
    long res = 0;
    if (_boosterBytes !=null) res+= _boosterBytes.length;
    return res;
  }

  public XGBoostModel.XGBoostParameters parameters;
  public final XGBoostModel.XGBoostParameters get_params() { return parameters; }

  private final boolean _classification; // Classification cache (nclasses>1)

  /**
   * Main constructor
   * @param origParams Model parameters
   * @param nClasses number of classes (1 for regression, 0 for autoencoder)
   */
  XGBoostModelInfo(final XGBoostModel.XGBoostParameters origParams, int nClasses) {
    _classes = nClasses;
    _classification = _classes > 1;
    parameters = (XGBoostModel.XGBoostParameters) origParams.clone(); //make a copy, don't change model's parameters
  }


  /**
   * Create a summary table
   * @return TwoDimTable with the summary of the model
   */
  TwoDimTable createSummaryTable() {
    TwoDimTable table = new TwoDimTable(
        "Status of XGBoost Model",
            "Ha",
        new String[1], //rows
        new String[]{"Input Neurons", "Rate", "Momentum" },
        new String[]{"int", "double", "double" },
        new String[]{"%d", "%5f", "%5f"},
        "");
    table.set(0, 0, 123);
    table.set(0, 1, 1234);
    table.set(0, 2, 12345);
    summaryTable = table;
    return summaryTable;
  }

  /**
   * Print a summary table
   * @return String containing ASCII version of summary table
   */
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    createSummaryTable();
    if (summaryTable!=null) sb.append(summaryTable.toString(1));
    return sb.toString();
  }
}
