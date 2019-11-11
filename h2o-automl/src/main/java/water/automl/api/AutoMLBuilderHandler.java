package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.Handler;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV99;


public class AutoMLBuilderHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLBuildSpecV99 build(int version, AutoMLBuildSpecV99 schema) {
    AutoMLBuildSpec buildSpec = schema.createAndFillImpl();
    AutoML aml = AutoML.startAutoML(buildSpec);
    schema.job = new JobV3().fillFromImpl(aml.job());
    schema.build_control.project_name = aml.projectName();
    return schema;
  }

  @SuppressWarnings("unused")
  public AutoMLBuildSpecV99 meta(int version, AutoMLBuildSpecV99 schema) {
    return schema;
  }
}
