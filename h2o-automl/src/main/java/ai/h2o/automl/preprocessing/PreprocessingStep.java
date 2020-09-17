package ai.h2o.automl.preprocessing;

import hex.Model;

public interface PreprocessingStep<T> {
    
    interface Completer extends Runnable {}
    
    void prepare();
    
    Completer apply(Model.Parameters params);
    
    void dispose();
    
}
