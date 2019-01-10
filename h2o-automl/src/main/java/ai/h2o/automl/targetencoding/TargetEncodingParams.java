package ai.h2o.automl.targetencoding;

import water.Iced;

public class TargetEncodingParams extends Iced {

  private boolean _withBlendedAvg;
  private BlendingParams _blendingParams;
  private byte _holdoutType;

  private boolean _imputeNAsWithNewCategory = true;

  public TargetEncodingParams( BlendingParams blendingParams, byte holdoutType) {
    if(blendingParams != null) this._withBlendedAvg = true;
    this._blendingParams = blendingParams;
    this._holdoutType = holdoutType;
  }
  
  public TargetEncodingParams( byte holdoutType) {
    this._withBlendedAvg = false;
    this._blendingParams = null;
    this._holdoutType = holdoutType;
  }

  public BlendingParams getBlendingParams() {
    return _blendingParams;
  }

  public byte getHoldoutType() {
    return _holdoutType;
  }

  public boolean isWithBlendedAvg() {
    return _withBlendedAvg;
  }

  public boolean isImputeNAsWithNewCategory() {
    return _imputeNAsWithNewCategory;
  }
}
