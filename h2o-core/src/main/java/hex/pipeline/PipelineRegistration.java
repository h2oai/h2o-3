package hex.pipeline;

import hex.pipeline.PipelineModel.PipelineParameters;
import water.AbstractH2OExtension;

public class PipelineRegistration extends AbstractH2OExtension {
  @Override
  public String getExtensionName() {
    return PipelineParameters.ALGO;
  }

  @Override
  public void init() {
    //enough to register the Pipeline as an algo as we don't need to expose it to REST API for now.
    new Pipeline(true);
  }
}
