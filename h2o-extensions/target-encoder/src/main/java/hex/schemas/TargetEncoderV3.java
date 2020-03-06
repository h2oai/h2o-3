package hex.schemas;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderBuilder;
import ai.h2o.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.ArrayList;
import java.util.List;

public class TargetEncoderV3 extends ModelBuilderSchema<TargetEncoderBuilder, TargetEncoderV3, TargetEncoderV3.TargetEncoderParametersV3> {
  public static class TargetEncoderParametersV3 extends ModelParametersSchemaV3<TargetEncoderModel.TargetEncoderParameters, TargetEncoderParametersV3> {
    
    @API(help = "Blending enabled/disabled")
    public boolean blending;

    @API(help = "Inflection point. Used for blending (if enabled). Blending is to be enabled separately using the 'blending' parameter.")
    public double k;

    @API(help = "Smoothing. Used for blending (if enabled). Blending is to be enabled separately using the 'blending' parameter.")
    public double f;

    @API(help = "Data leakage handling strategy.", values = {"None", "KFold", "LeaveOneOut"})
    public TargetEncoder.DataLeakageHandlingStrategy data_leakage_handling;

    @API(help = "Noise level", required = false, direction = API.Direction.INPUT, gridable = true)
    public double noise_level;
    
    @API(help = "Seed for the specified noise level", required = false, direction = API.Direction.INPUT)
    public long seed;
  
    @Override
    public String[] fields() {
      final List<String> params = new ArrayList<>();
      params.add("model_id");
      params.add("training_frame");
      params.add("fold_column");
      params.add("response_column");
      params.add("ignored_columns");
      params.addAll(extractDeclaredApiParameters(getClass()));

      return params.toArray(new String[0]);
    }

    @Override
    public TargetEncoderParametersV3 fillFromImpl(TargetEncoderModel.TargetEncoderParameters impl) {
      return fillFromImpl(impl, new String[0]);
    }

    @Override
    protected TargetEncoderParametersV3 fillFromImpl(TargetEncoderModel.TargetEncoderParameters impl, String[] fieldsToSkip) {
      final TargetEncoderParametersV3 teParamsV3 = super.fillFromImpl(impl, fieldsToSkip);
      teParamsV3.k = impl._k;
      teParamsV3.f = impl._f;
      return teParamsV3;
    }
  }
}
