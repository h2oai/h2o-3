package ml.dmlc.xgboost4j.java;

import hex.DataInfo;
import hex.tree.xgboost.XGBoostModel;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.BoosterHelper;
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
  public byte[] _boosterBytes; // internal state of native backend

  private TwoDimTable summaryTable;

  private transient Booster _booster;  //pointer to C++ process

  public Booster getBooster() {
    if(null == _booster && null != _boosterBytes) {
      try {
        _booster = Booster.loadModel(new ByteArrayInputStream(_boosterBytes));
      } catch (XGBoostError | IOException exception) {
        throw new IllegalStateException("Failed to load the booster.", exception);
      }
    }

    return _booster;
  }

  public void setBooster(Booster _booster) {
    this._booster = _booster;
  }

  public Key<DataInfo> _dataInfoKey;

  public final Booster booster() {
    if (_booster == null) {
      // We do not synchronize here since the booster should be setup/read
      // only by single threaded driver, the same for setter below
      _booster = javaToNative(_boosterBytes);
    }
    return _booster;
  }

  public void nukeBackend() {
    if (_booster != null) {
      _booster.dispose();
    }
    _booster = null;
  }

  public void nativeToJava() {
    try {
      _boosterBytes = _booster.toByteArray();
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
      throw new RuntimeException(xgBoostError);
    }
  }

  private static Booster javaToNative(byte[] boosterBytes) {
    InputStream is = new ByteArrayInputStream(boosterBytes);
    try {
      return BoosterHelper.loadModel(is);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
  public XGBoostModelInfo(final XGBoostModel.XGBoostParameters origParams, int nClasses) {
    _classification = nClasses > 1;
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
