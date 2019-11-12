package hex.schemas;

import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderTransformParameters;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TargetEncoderTransformParametersV3 extends SchemaV3<TargetEncoderTransformParameters, TargetEncoderTransformParametersV3> {

  @API(help = "Target Encoder model to use.")
  public KeyV3.ModelKeyV3<TargetEncoderModel> model;
  @API(help = "Seed value")
  public long seed;
  @API(help = "Data leakage handling strategy.", valuesProvider = DataLeakageHandlingStrategyProvider.class)
  public TargetEncoder.DataLeakageHandlingStrategy data_leakage_handling;
  @API(help = "Noise")
  public double noise;
  @API(help = "Frame to transform")
  public KeyV3.FrameKeyV3 frame;
  @API(help = "Enables or disables blending")
  public boolean blending;
  @API(help = "Inflection point")
  public double inflection_point;
  @API(help = "Smoothing")
  public double smoothing;

  public static final class DataLeakageHandlingStrategyProvider extends EnumValuesProvider<TargetEncoder.DataLeakageHandlingStrategy> {
    public DataLeakageHandlingStrategyProvider() { super(TargetEncoder.DataLeakageHandlingStrategy.class); }
  }

}
