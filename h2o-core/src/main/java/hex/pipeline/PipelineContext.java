package hex.pipeline;

import hex.pipeline.PipelineModel.PipelineParameters;
import water.Iced;

public class PipelineContext extends Iced<PipelineContext> {

  public final PipelineParameters _params;

  public PipelineContext(PipelineParameters params) {
    _params = params;
  }

}
