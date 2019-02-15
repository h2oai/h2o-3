package ai.h2o.automl.targetencoding;

import water.Iced;

import java.util.Map;

public class TargetEncodingParams extends Iced {

  private boolean _withBlendedAvg;
  private BlendingParams _blendingParams;
  private byte _holdoutType;
  private double _noiseLevel;

  private boolean _imputeNAsWithNewCategory = true;

  public TargetEncodingParams( BlendingParams blendingParams, byte holdoutType, double noiseLevel) {
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
  
  public TargetEncodingParams( Map<String, Object> paramsMap) {
    this._withBlendedAvg = (boolean) paramsMap.get("_withBlending");;
    this._blendingParams = new BlendingParams((int) paramsMap.get("_inflection_point"), (double) paramsMap.get("_smoothing"));
    this._holdoutType = (byte) paramsMap.get("_holdoutType");
    this._noiseLevel = (double) paramsMap.get("_noise_level");
  }
  
  public static TargetEncodingParams DEFAULT = new TargetEncodingParams(new BlendingParams(10, 5), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);

  public BlendingParams getBlendingParams() {
    return _blendingParams;
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
