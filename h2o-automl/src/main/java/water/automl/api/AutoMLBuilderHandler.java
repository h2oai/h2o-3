package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.Handler;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV99;


public class AutoMLBuilderHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLBuildSpecV99 build(int version, AutoMLBuildSpecV99 buildSpecSchema) {
    AutoMLBuildSpec buildSpec = buildSpecSchema.createAndFillImpl();
    AutoML aml = AutoML.startAutoML(buildSpec);
    buildSpecSchema.job = new JobV3().fillFromImpl(aml.job());
    return buildSpecSchema;
  }
}
