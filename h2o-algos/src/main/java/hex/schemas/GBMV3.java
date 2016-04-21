package hex.schemas;

import hex.Distribution;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.api.API;


public class GBMV3 extends SharedTreeV3<GBM,GBMV3,GBMV3.GBMParametersV3> {

  public static final class GBMParametersV3 extends SharedTreeV3.SharedTreeParametersV3<GBMParameters, GBMParametersV3> {
    static public String[] fields = new String[] {
      "model_id",
      "training_frame",
      "validation_frame",
      "nfolds",
      "keep_cross_validation_predictions",
      "keep_cross_validation_fold_assignment",
      "score_each_iteration",
      "score_tree_interval",
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
      "max_runtime_secs",
      "seed",
      "build_tree_one_node",
      "learn_rate",
      "learn_rate_annealing",
      "distribution",
      "quantile_alpha",
      "tweedie_power",
      "checkpoint",
      "sample_rate",
      "sample_rate_per_class",
      "col_sample_rate",
      "col_sample_rate_change_per_level",
      "col_sample_rate_per_tree",
      "min_split_improvement",
      "random_split_points",
      "max_abs_leafnode_pred"
    };

    // Input fields
    @API(help="Learning rate (from 0.0 to 1.0)", gridable = true)
    public double learn_rate;

    @API(help="Scale down the learning rate by this factor after each tree", level = API.Level.expert, gridable = true)
    public double learn_rate_annealing;

    @API(help = "Distribution function", values = { "AUTO", "bernoulli", "multinomial", "gaussian", "poisson", "gamma", "tweedie", "laplace", "quantile" }, gridable = true)
    public Distribution.Family distribution;

    @API(help="Desired quantile for quantile regression (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double quantile_alpha;

    @API(help = "Tweedie Power (between 1 and 2)", level = API.Level.secondary, gridable = true)
    public double tweedie_power;

    @API(help="Column sample rate (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double col_sample_rate;

    @API(help="Maximum absolute value of a leaf node prediction", level = API.Level.expert, gridable = true)
    public double max_abs_leafnode_pred;

//    @API(help = "Quantile for quantile regression (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
//    public double quantile;
  }
}
