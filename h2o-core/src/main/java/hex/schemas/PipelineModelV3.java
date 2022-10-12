package hex.schemas;

import hex.pipeline.PipelineModel;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class PipelineModelV3 extends ModelSchemaV3<
        PipelineModel, PipelineModelV3, 
        PipelineParameters, PipelineV3.PipelineParametersV3, 
        PipelineOutput, PipelineModelV3.PipelineModelOutputV3
        > {


  public static final class PipelineModelOutputV3 extends ModelOutputSchemaV3<PipelineOutput, PipelineModelOutputV3> {
    
  }
}
