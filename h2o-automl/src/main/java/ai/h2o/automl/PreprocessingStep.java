package ai.h2o.automl;

import ai.h2o.automl.preprocessing.Preprocessor;
import hex.Model;
import hex.ModelBuilder;
import water.Iced;

/**
 * tbd
 * PM preprocessing model. Maybe we need marker interface to distinguish between predicting models and
 */
public abstract class PreprocessingStep<PM extends Model> extends Iced<PreprocessingStep> {

    protected transient AutoML _aml;

    protected final Preprocessor _preprocessor;

    protected PreprocessingStep(Preprocessor preprocessor, AutoML autoML) {
        _preprocessor = preprocessor;
        _aml = autoML;
    }

    protected abstract void applyIfUseful(ModelBuilder modelBuilder);
}
