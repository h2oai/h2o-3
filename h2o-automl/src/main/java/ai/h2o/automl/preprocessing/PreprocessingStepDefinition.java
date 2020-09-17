package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import water.Iced;

public class PreprocessingStepDefinition extends Iced<PreprocessingStepDefinition> {
    
    public enum Type {
        TargetEncoding
    }
    
    Type _type;

    public PreprocessingStep newPreprocessingStep(AutoML aml) {
        switch (_type) {
            case TargetEncoding:
                return new TargetEncoding(aml);
            default:
                throw new IllegalStateException();
        }
    }
}
