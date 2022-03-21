package hex.schemas;

import hex.modelselection.ModelSelectionModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class ModelSelectionModelV3 extends ModelSchemaV3<ModelSelectionModel, ModelSelectionModelV3, ModelSelectionModel.ModelSelectionParameters,
        ModelSelectionV3.ModelSelectionParametersV3, ModelSelectionModel.ModelSelectionModelOutput, ModelSelectionModelV3.ModelSelectionModelOutputV3> {
    public static final class ModelSelectionModelOutputV3 extends ModelOutputSchemaV3<ModelSelectionModel.ModelSelectionModelOutput,
            ModelSelectionModelOutputV3> {
        
        @API(help="Best predictor subset names for each subset size.")
        String[][] best_model_predictors; // store for each predictor number, the best model predictors
        
        @API(help="R2 values of all possible predictor subsets.  Only for model='allsubsets' or 'maxr'.")
        double[] best_r2_values;  // store the best R2 values of the best models with fix number of predictors

        @API(help="p-values of chosen predictor subsets at each subset size. Only for model='backward'.")
        double[][] coef_p_values;

        @API(help="z-values of chosen predictor subsets at each subset size. Only for model='backward'.")
        double[][] z_values;
        
        @API(help="Key of models containing best 1-predictor model, best 2-predictors model, ....")
        KeyV3.ModelKeyV3[] best_model_ids;
        
        @API(help="arrays of string arrays containing coefficient names of best 1-predictor model, best 2-predictors model, ....")
        String[][] coefficient_names;
        
        @Override
        public ModelSelectionModelOutputV3 fillFromImpl(ModelSelectionModel.ModelSelectionModelOutput impl) {
            super.fillFromImpl(impl);   // fill in the best_model_predictors_r2 table here when done
            return this;
        }
        
    }
    public ModelSelectionV3.ModelSelectionParametersV3 createParametersSchema() { return new ModelSelectionV3.ModelSelectionParametersV3(); }
    public ModelSelectionModelOutputV3 createOutputSchema() { return new ModelSelectionModelOutputV3();}
    
    @Override
    public ModelSelectionModel createImpl() {
        ModelSelectionModel.ModelSelectionParameters parms = parameters.createImpl();
        return new ModelSelectionModel(model_id.key(), parms, null);
    }
}
