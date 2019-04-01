package ai.h2o.automl.targetencoding;

import water.Iced;

import java.util.ArrayList;
import java.util.Map;

public class TargetEncodingParams extends Iced {

  private boolean _withBlendedAvg;
  private BlendingParams _blendingParams;
  private byte _holdoutType;
  private double _noiseLevel;

  private String[] _columnsToEncode;

  private boolean _imputeNAsWithNewCategory = true;

  public TargetEncodingParams(String[] columnsToEncode, BlendingParams blendingParams, byte holdoutType, double noiseLevel) {
    _columnsToEncode = columnsToEncode;
    if(blendingParams != null) this._withBlendedAvg = true;
    this._blendingParams = blendingParams;
    this._holdoutType = holdoutType;
    this._noiseLevel = noiseLevel;
  }
  
  public TargetEncodingParams( byte holdoutType) {
    this._withBlendedAvg = false;
    this._blendingParams = null;
    this._holdoutType = holdoutType;
    this._noiseLevel = 0;
  }
  
  public TargetEncodingParams( Map<String, Object> gridEntry) {
    _withBlendedAvg = (double) gridEntry.get("_withBlending") == 1.0;
    _blendingParams = new BlendingParams((double) gridEntry.get("_inflection_point"), (double) gridEntry.get("_smoothing"));
    
    double value = (double) gridEntry.get("_holdoutType");
    if(value == 0.0) _holdoutType = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
    if(value == 1.0) _holdoutType = TargetEncoder.DataLeakageHandlingStrategy.KFold;
    if(value == 2.0) _holdoutType = TargetEncoder.DataLeakageHandlingStrategy.None;

    Object noise_level = gridEntry.get("_noise_level");
    _noiseLevel = noise_level != null ? (double) noise_level : 0.0;

    _columnsToEncode = extractColumnsToEncodeFromGridEntry(gridEntry);
  }

  
  private String[] extractColumnsToEncodeFromGridEntry(Map<String, Object> gridEntry) {
    ArrayList<String> columnsIdxsToEncode = new ArrayList();
    for (Map.Entry<String, Object> entry : gridEntry.entrySet()) {
      String column_to_encode_prefix = "_column_to_encode_";
      if(entry.getKey().contains(column_to_encode_prefix)) {
        double entryValue = (double) entry.getValue();
        if(entryValue != -1.0)
          columnsIdxsToEncode.add(entry.getKey().substring(column_to_encode_prefix.length()));
      }

    }
    return columnsIdxsToEncode.toArray(new String[]{});
  }

  public String[] getColumnsToEncode() {
    return _columnsToEncode;
  }
  
  public BlendingParams getBlendingParams() {
    return _blendingParams;
  }

  @Override
  public String toString() {
    String representation = null;
    if( isWithBlendedAvg()) {
      representation = "TE params: holdout_type = " + getHoldoutType() + " , blending = " + isWithBlendedAvg() + ", inflection_point = " + getBlendingParams().getK() +
              " , smoothing = " + getBlendingParams().getF() + " , noise_level = " + getNoiseLevel();
    }
    else {
      representation = "TE params: holdout_type = " + getHoldoutType() + " , noise_level = " + getNoiseLevel();
    }
    return representation;
  }

  public byte getHoldoutType() {
    return _holdoutType;
  }

  public double getNoiseLevel() {
    return _noiseLevel;
  }

  public boolean isWithBlendedAvg() {
    return _withBlendedAvg;
  }

  public boolean isImputeNAsWithNewCategory() {
    return _imputeNAsWithNewCategory;
  }
}
