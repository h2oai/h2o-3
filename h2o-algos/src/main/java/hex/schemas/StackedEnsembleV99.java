package hex.schemas;

import hex.ensemble.StackedEnsemble;
import hex.StackedEnsembleModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;


public class StackedEnsembleV99 extends ModelBuilderSchema<StackedEnsemble,StackedEnsembleV99,StackedEnsembleV99.StackedEnsembleParametersV99> {
  public static final class StackedEnsembleParametersV99 extends ModelParametersSchemaV3<StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleParametersV99> {
    static public String[] fields = new String[]{
      "model_id",
      "training_frame",
      "response_column",
      "validation_frame",
      "base_models",
      "keep_levelone_frame",
      //"selection_strategy",
    };

    /*
    @API(help = "Strategy for choosing which models to stack.", values = { "choose_all" }, gridable = false)
    public StackedEnsembleModel.StackedEnsembleParameters.SelectionStrategy selection_strategy;

    @API(help = "List of model ids which we can stack together.  Which ones are chosen depends on the selection_strategy (currently, all models will be used since selection_strategy can only be set to choose_all).  Models must have been cross-validated using nfolds > 1, fold_assignment equal to Modulo, and keep_cross_validation_folds must be set to True.", required = true)
    public KeyV3.ModelKeyV3 base_models[];
    */

    @API(help = "List of model ids which we can stack together. Models must have been cross-validated using nfolds > 1, and folds must be identical across models.", required = true)
    public KeyV3.ModelKeyV3 base_models[];

    @API(help = "Keep level one frame used for metalearner training.")
    public boolean keep_levelone_frame;
  }
}
