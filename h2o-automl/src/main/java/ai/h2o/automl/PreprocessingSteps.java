package ai.h2o.automl;

import water.Iced;

public abstract class PreprocessingSteps extends Iced<PreprocessingSteps> {

    private transient AutoML _aml;

    public PreprocessingSteps(AutoML autoML) {
        _aml = autoML;
    }

    protected AutoML aml() {
        return _aml;
    }

    //TODO can be package private if we move this into automl package
    public PreprocessingStep[] getSteps() {
        return getDefaultPreprocessors();
    }

    protected PreprocessingStep[] getDefaultPreprocessors() { return new PreprocessingStep[0]; }

}
