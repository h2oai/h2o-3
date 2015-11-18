package hex.schemas;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel.DRFParameters;
import water.api.API;

public class DRFV3 extends SharedTreeV3<DRF,DRFV3, DRFV3.DRFParametersV3> {

  public static final class DRFParametersV3 extends SharedTreeV3.SharedTreeParametersV3<DRFParameters, DRFParametersV3> {
    static public String[] fields = new String[] {
				"model_id",
				"training_frame",
				"validation_frame",
        "nfolds",
        "keep_cross_validation_predictions",
        "score_each_iteration",
        "fold_assignment",
        "fold_column",
				"response_column",
				"ignored_columns",
				"ignore_const_cols",
				"offset_column",
				"weights_column",
				"balance_classes",
				"class_sampling_factors",
				"max_after_balance_size",
				"max_confusion_matrix_size",
				"max_hit_ratio_k",
				"ntrees",
				"max_depth",
				"min_rows",
				"nbins",
        "nbins_top_level",
				"nbins_cats",
				"r2_stopping",
        "stopping_rounds",
        "stopping_metric",
        "stopping_tolerance",
				"seed",
				"build_tree_one_node",
        "mtries",
        "sample_rate",
        "binomial_double_trees",
        "checkpoint"
    };

    // Input fields
    @API(help = "Number of variables randomly sampled as candidates at each split. If set to -1, defaults to sqrt{p} for classification and p/3 for regression (where p is the # of predictors", gridable = true)
    public int mtries;

    @API(help="For binary classification: Build 2x as many trees (one per class) - can lead to higher accuracy.", level = API.Level.secondary)
    public boolean binomial_double_trees;
  }
}
