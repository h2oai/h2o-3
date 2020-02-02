package ai.h2o.automl.preprocessing;

import ai.h2o.automl.*;

/**
 * Universal means applies for any kind of models
 */
public class UniversalPreprocessingSteps extends PreprocessingSteps {

  private boolean _teEnabled;

  private PreprocessingStep[] defaults = _teEnabled ?
          new PreprocessingStep[]{new TEPreprocessingStep(Preprocessor.TE, aml())} : new PreprocessingStep[0];

  public UniversalPreprocessingSteps(AutoML autoML) {
    super(autoML);
    _teEnabled = autoML.getBuildSpec().te_spec.enabled;
  }

  @Override
  protected PreprocessingStep[] getDefaultPreprocessors() {
    return defaults;
  }

}

