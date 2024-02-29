package ai.h2o.automl.preprocessing;

import hex.pipeline.DataTransformer;

import java.util.Map;

public interface PipelineStep {

  String getType();

  /**
   * 
   * @return an array of pipeline {@link hex.pipeline.DataTransformer}s needed for this pipeline step.
   */
  DataTransformer[] pipelineTransformers();

  /**
   * 
   * @return a map of hyper-parameters for the {@link hex.pipeline.DataTransformer}s of this pipeline step
   *         that can be used in AutoML grids.
   */
  Map<String, Object[]> pipelineTransformersHyperParams();
}
