package hex.schemas;

import ai.h2o.automl.targetencoding.TargetEncoderBuilder;
import ai.h2o.automl.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.List;

public class TargetEncoderV3 extends ModelBuilderSchema<TargetEncoderBuilder, TargetEncoderV3, TargetEncoderV3.TargetEncoderParametersV3> {
  public static class TargetEncoderParametersV3 extends ModelParametersSchemaV3<TargetEncoderModel.TargetEncoderParameters, TargetEncoderParametersV3> {
    
    @API(help = "Is blending used ? True by default.", mapsTo = "_withBlending")
    public boolean blending;
    
    @API(help = "Columnds to encode.", mapsTo = "_columnNamesToEncode")
    public String[] encoded_columns;
    
    @API(help = "Target column for the encoding", mapsTo = "_response_column")
    public String target_column;
    
    @API(help = "Parameters used for blending (if enabled). Blending is to be enabled separaterly using the 'blending' parameter.", mapsTo = "_blendingParams")
    public BlendingParamsV3 blending_parameters;

    @API(help = "Data leakage handling strategy. Default to None.", mapsTo = "_leakageHandlingStrategy")
    public String data_leakage_handling;
  
    @Override
    public String[] fields() {
      final List<String> params = extractDeclaredApiParameters(getClass());
      params.add("model_id");
  
      return params.toArray(new String[0]);
    }
  }
}
