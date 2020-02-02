package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.PreprocessingStep;
import ai.h2o.automl.targetencoder.AutoMLTargetEncoderAssistant;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;
import water.fvec.Frame;

import java.util.Optional;

public class TEPreprocessingStep extends PreprocessingStep<TargetEncoderModel> {

  public TEPreprocessingStep(Preprocessor preprocessor, AutoML autoML) {
    super(preprocessor, autoML);
  }

  @Override
  protected void applyIfUseful(ModelBuilder modelBuilder) {
    AutoMLBuildSpec buildSpec = _aml.getBuildSpec();

    Frame originalTrain = modelBuilder._parms.train();
    // NOTE: here we will also affect `_buildSpec.input_spec.ignored_columns`. We will switch it back below in `finally` block after model is trained.
    String[] originalIgnoredColumns = buildSpec.input_spec.ignored_columns == null ? null : buildSpec.input_spec.ignored_columns.clone();

    AutoMLTargetEncoderAssistant<TargetEncoderModel.TargetEncoderParameters> teAssistant = new AutoMLTargetEncoderAssistant<>(_aml,
            buildSpec,
            modelBuilder);

    Optional<TargetEncoderModel.TargetEncoderParameters> bestTEParamsOpt = teAssistant.findBestTEParams();

    //TODO store parameters/or better best Model if proved useful  inside ModelBuilder (or inside ModelPipelineBuilder class).
    // ModelPipeline should has references to preprocessing Models to apply before scoring

    // TODO Evaluation of particular preprocessing parameters should be done with Model

  }
}
