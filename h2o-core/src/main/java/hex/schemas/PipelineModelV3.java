package hex.schemas;

import hex.pipeline.PipelineModel;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class PipelineModelV3 extends ModelSchemaV3<
        PipelineModel, PipelineModelV3, 
        PipelineParameters, PipelineV3.PipelineParametersV3, 
        PipelineOutput, PipelineModelV3.PipelineModelOutputV3
        > {

  @Override
  public PipelineV3.PipelineParametersV3 createParametersSchema() {
    return new PipelineV3.PipelineParametersV3();
  }

  @Override
  public PipelineModelOutputV3 createOutputSchema() {
    return new PipelineModelOutputV3();
  }

  public static final class PipelineModelOutputV3 extends ModelOutputSchemaV3<PipelineOutput, PipelineModelOutputV3> {

    @API(help="Sequence of transformers applied to input data.", direction = API.Direction.OUTPUT)
    public KeyV3.DataTransformerKeyV3[] transformers;

    @API(help="Estimator model trained and/or applied after transformations.", direction = API.Direction.OUTPUT)
    public KeyV3.ModelKeyV3 estimator;

    @Override
    public PipelineModelOutputV3 fillFromImpl(PipelineOutput impl) {
      return super.fillFromImpl(impl);
    }
  }
}
