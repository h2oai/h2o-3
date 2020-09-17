package ai.h2o.automl.preprocessing;

import hex.Model;

public interface PreprocessingStep<T> {

    interface Completer extends Runnable {}
    
    String getType();

    /**
     * preprocessing steps are prepared by default before the AutoML session starts training the first model.
     */
    void prepare();

    /**
     * applies this preprocessing step to the model parameters right before the model training starts.
     * @param params
     * @return a function used to "complete" the preprocessing step: it is called by default at the end of the job creating model(s) from the given parms.
     * This can mean for example cleaning the temporary artifacts that may have been created to apply the preprocessing step.
     */
    Completer apply(Model.Parameters params);

    /**
     * preprocessing steps are disposed by default at the end of the AutoML training session.
     * Note that disposing here doesn't mean being removed from the system, 
     * the goal is mainly to clean resources that are not needed anymore for the current AutoML run.
     */
    void dispose();

    /**
     * Completely remove from the system
     */
    void remove();
    
}
