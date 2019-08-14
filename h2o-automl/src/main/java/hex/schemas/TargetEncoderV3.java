package hex.schemas;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoderBuilder;
import ai.h2o.automl.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.schemas3.FrameV3;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.List;

public class TargetEncoderV3 extends ModelBuilderSchema<TargetEncoderBuilder, TargetEncoderV3, TargetEncoderV3.TargetEncoderParametersV3> {
  public static class TargetEncoderParametersV3 extends ModelParametersSchemaV3<TargetEncoderModel.TargetEncoderParameters, TargetEncoderParametersV3> {
    
    @API(help = "Is blending used ? True if blending is used, false if not. True by default.")
    public boolean blending;
    
    @API(help = "Columnds to encode.")
    public FrameV3.ColSpecifierV3[] encoded_columns;
    
    @API(help = "Target column for the encoding")
    public FrameV3.ColSpecifierV3 target_column;
    
    @API(help = "Parameter 'f' used for blending (if enabled). Blending is to be enabled separaterly using the 'blending' parameter.")
    public double k;

    @API(help = "Parameter 'k' used for blending (if enabled). Blending is to be enabled separaterly using the 'blending' parameter.")
    public double f;

    @API(help = "Data leakage handling strategy. Default to None.")
    public String data_leakage_handling;
  
    @Override
    public String[] fields() {
      final List<String> params = extractDeclaredApiParameters(getClass());
      params.add("model_id");
      params.add("fold_column");
  
      return params.toArray(new String[0]);
    }

    @Override
    public TargetEncoderParametersV3 fillFromImpl(TargetEncoderModel.TargetEncoderParameters impl) {
      super.fillFromImpl(impl);
      if(impl._blending_parameters != null) {
        k = impl._blending_parameters.getK();
        f = impl._blending_parameters.getF();
      }
      return this;
    }

    @Override
    public TargetEncoderModel.TargetEncoderParameters fillImpl(TargetEncoderModel.TargetEncoderParameters impl) {
      super.fillImpl(impl);
      impl._blending_parameters = new BlendingParams(k,f);
      impl._response_column = target_column.column_name;
      return impl;
    }
  }
}
