package ai.h2o.automl.preprocessing;

import ai.h2o.automl.*;

import java.util.ArrayList;

/**
 * Universal means applies for any kind of models
 */
public class UniversalPreprocessingSteps extends PreprocessingSteps {

  private ArrayList<PreprocessingStep> defaults = new ArrayList<>();

  public UniversalPreprocessingSteps(AutoML autoML) {
    super(autoML);
    if(autoML.getBuildSpec().te_spec.enabled)
      defaults.add(new TEPreprocessingStep(Preprocessor.TE, aml()));
  }

  @Override
  protected PreprocessingStep[] getDefaultPreprocessors() {
    return defaults.toArray(new PreprocessingStep[0]);
  }

}

