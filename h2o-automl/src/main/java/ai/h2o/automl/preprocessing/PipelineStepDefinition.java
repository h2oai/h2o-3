package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import water.Iced;

public class PipelineStepDefinition extends Iced<PipelineStepDefinition> {
    
    public enum Type {
        TargetEncoding
    }

    Type _type;

    public PipelineStepDefinition() { /* for reflection */ }

    public PipelineStepDefinition(Type type) { 
        _type = type;
    }

    public PipelineStep newPipelineStep(AutoML aml) {
        switch (_type) {
            case TargetEncoding:
                return new TargetEncoding(aml);
            default:
                throw new IllegalStateException();
        }
    }
}
