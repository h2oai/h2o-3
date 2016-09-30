package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.Handler;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV3;


public class AutoMLBuilderHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLBuildSpecV3 build(int version, AutoMLBuildSpecV3 buildSpecSchema) {
    AutoMLBuildSpec buildSpec = buildSpecSchema.createAndFillImpl();
/*
    Frame trainingFrame =
            (null == buildSpecSchema.input_spec.import_training_files?
                    null : (Frame)DKV.getGet(buildSpecSchema.input_spec.import_training_files.path));
    Frame validationFrame =
            (null == buildSpecSchema.input_spec.import_validation_files?
                    null : (Frame)DKV.getGet(buildSpecSchema.input_spec.import_validation_files.path));
                    */

    if (buildSpec.input_spec.training_frame != null && buildSpec.input_spec.training_path !=null)
      throw new IllegalArgumentException("Both training_frame and training_files were specified; you must choose one or the other!");
    if (buildSpec.input_spec.validation_frame != null && buildSpec.input_spec.validation_path !=null)
      throw new IllegalArgumentException("Both validation_frame and validation_files were specified; you must choose one or the other!");

    AutoML aml;
    aml = AutoML.startAutoML(buildSpec);
    buildSpecSchema.job = new JobV3().fillFromImpl(aml.job());
    return buildSpecSchema;
  }
}
