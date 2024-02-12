package hex.schemas;

import hex.pipeline.Pipeline;
import hex.pipeline.PipelineModel;
import water.api.schemas3.ModelParametersSchemaV3;

public class PipelineV3 extends ModelBuilderSchema<Pipeline, PipelineV3, PipelineV3.PipelineParametersV3> {

  public static final class PipelineParametersV3 extends ModelParametersSchemaV3<PipelineModel.PipelineParameters, PipelineParametersV3> {
    static public String[] fields = new String[] {
            "model_id",
//            "training_frame",
//            "validation_frame",
//            "response_column",
//            "nfolds",
//            "fold_column",
    };
    
  }
}
