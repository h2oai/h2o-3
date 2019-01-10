package ai.h2o.automl.targetencoding;

public class TargetEncodingParams {
  private boolean _withBlendedAvg;
  private BlendingParams _blendingParams;
  private double _holdoutType;
  private boolean imputeNAsWithNewCategory = true;

  public TargetEncodingParams( BlendingParams blendingParams, double holdoutType) {
    if(blendingParams != null) this._withBlendedAvg = true;
    this._blendingParams = blendingParams;
    this._holdoutType = holdoutType;
  }
  
  public TargetEncodingParams( double holdoutType) {
    this._withBlendedAvg = false;
    this._blendingParams = null;
    this._holdoutType = holdoutType;
  }
}
