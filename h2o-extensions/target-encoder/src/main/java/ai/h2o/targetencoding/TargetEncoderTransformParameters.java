package ai.h2o.targetencoding;

import water.Iced;
import water.Key;
import water.fvec.Frame;

public class TargetEncoderTransformParameters extends Iced<TargetEncoderTransformParameters> {
  
  public Key<TargetEncoderModel> _model;
  public Key<Frame> _frame;
  public long _seed;
  public TargetEncoder.DataLeakageHandlingStrategy _data_leakage_handling;
  public double _noise;
  
}
