package hex.schemas;

import hex.maxrglm.MaxRGLMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;

public class MaxRGLMModelV3 extends ModelSchemaV3<MaxRGLMModel, MaxRGLMModelV3, MaxRGLMModel.MaxRGLMParameters, 
        MaxRGLMV3.MaxRGLMParametersV3, MaxRGLMModel.MaxRGLMModelOutput, MaxRGLMModelV3.MaxRGLMModelOutputV3> {
    
    public static final class MaxRGLMModelOutputV3 extends ModelOutputSchemaV3<MaxRGLMModel.MaxRGLMModelOutput, 
            MaxRGLMModelOutputV3> {
        
        @API(help="best predictor subset names for each subset size")
        String[][] best_model_predictors; // store for each predictor number, the best model predictors
        
        @API(help="R2 values of all possible predictor subsets")
        double[] best_r2_values;  // store the best R2 values of the best models with fix number of predictors
        
        @API(help="Key of models containing best 1-predictor model, best 2-predictors model, ....")
        KeyV3.ModelKeyV3[] best_model_ids;
        
        @Override
        public MaxRGLMModelOutputV3 fillFromImpl(MaxRGLMModel.MaxRGLMModelOutput impl) {
            super.fillFromImpl(impl);   // fill in the best_model_predictors_r2 table here when done
            return this;
        }
        
    }
    public MaxRGLMV3.MaxRGLMParametersV3 createParametersSchema() { return new MaxRGLMV3.MaxRGLMParametersV3(); }
    public MaxRGLMModelOutputV3 createOutputSchema() { return new MaxRGLMModelOutputV3();}
    
    @Override
    public MaxRGLMModel createImpl() {
        MaxRGLMModel.MaxRGLMParameters parms = parameters.createImpl();
        return new MaxRGLMModel(model_id.key(), parms, null);
    }
}
