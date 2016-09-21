package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.TimedH2OJob;
import water.DKV;
import water.Key;
import water.api.Handler;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV3;
import water.parser.ParseSetup;


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

    if (buildSpec.input_spec.training_frame != null && buildSpec.input_spec.training_files !=null)
      throw new IllegalArgumentException("Both training_frame and training_files were specified; you must choose one or the other!");
    if (buildSpec.input_spec.validation_frame != null && buildSpec.input_spec.validation_files !=null)
      throw new IllegalArgumentException("Both validation_frame and validation_files were specified; you must choose one or the other!");

    AutoML aml;
    aml = AutoML.makeAutoML(Key.<AutoML>make(), buildSpec);
    DKV.put(aml);
    buildSpecSchema.job = new JobV3().fillFromImpl(new TimedH2OJob(aml,aml._key).start());
    return buildSpecSchema;
  }
}
