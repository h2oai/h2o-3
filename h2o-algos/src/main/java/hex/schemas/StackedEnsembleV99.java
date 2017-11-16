package hex.schemas;

import hex.ensemble.StackedEnsemble;
import hex.StackedEnsembleModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.FrameV3;
import hex.Model;



public class StackedEnsembleV99 extends ModelBuilderSchema<StackedEnsemble,StackedEnsembleV99,StackedEnsembleV99.StackedEnsembleParametersV99> {
  public static final class StackedEnsembleParametersV99 extends ModelParametersSchemaV3<StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleParametersV99> {
    static public String[] fields = new String[] {
      "model_id",
      "training_frame",
      "response_column",
      "validation_frame",
      "base_models",
      "keep_levelone_frame",
      "metalearner_nfolds",
      "metalearner_fold_assignment",
      "metalearner_fold_column",
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

    @API(help = "Number of folds for k-fold cross-validation of the metalearner algorithm (default is 0: no cross-validation).")
    public int metalearner_nfolds;

    // For Ensemble cross-validation
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
            is_member_of_frames = {"training_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column", "weights_column", "offset_column"},
            help = "Column with cross-validation fold index assignment per observation.")
    public FrameV3.ColSpecifierV3 metalearner_fold_column;

    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
            values = {"AUTO", "Random", "Modulo", "Stratified"},
            help = "Cross-validation fold assignment scheme, if fold_column is not specified. The 'Stratified' option will " +
                    "stratify the folds based on the response variable, for classification problems.")
    public Model.Parameters.FoldAssignmentScheme metalearner_fold_assignment;
  }
}
