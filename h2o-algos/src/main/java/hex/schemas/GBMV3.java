package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.api.API;
import water.api.schemas3.KeyValueV3;


public class GBMV3 extends SharedTreeV3<GBM,GBMV3,GBMV3.GBMParametersV3> {

  public static final class GBMParametersV3 extends SharedTreeV3.SharedTreeParametersV3<GBMParameters, GBMParametersV3> {
    static public String[] fields = new String[] {
      "model_id",
      "training_frame",
      "validation_frame",
      "nfolds",
      "keep_cross_validation_models",
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
      "huber_alpha",
      "checkpoint",
      "sample_rate",
      "sample_rate_per_class",
      "col_sample_rate",
      "col_sample_rate_change_per_level",
      "col_sample_rate_per_tree",
      "min_split_improvement",
      "histogram_type",
      "max_abs_leafnode_pred",
      "pred_noise_bandwidth",
      "categorical_encoding",
      "calibrate_model",
      "calibration_frame",
      "calibration_method",
      "custom_metric_func",
      "custom_distribution_func",      
      "export_checkpoints_dir",
      "in_training_checkpoints_dir",
      "in_training_checkpoints_tree_interval",
      "monotone_constraints",
      "check_constant_response",
      "gainslift_bins", 
      "auc_type", 
      "interaction_constraints"
    };

    // Input fields
    @API(help="Learning rate (from 0.0 to 1.0)", gridable = true)
    public double learn_rate;

    @API(help="Scale the learning rate by this factor after each tree (e.g., 0.99 or 0.999) ", level = API.Level.secondary, gridable = true)
    public double learn_rate_annealing;

    @API(help = "Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
    public double sample_rate;

    @API(help="Column sample rate (from 0.0 to 1.0)", level = API.Level.critical, gridable = true)
    public double col_sample_rate;

    @API(help = "A mapping representing monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint.", level = API.Level.secondary)
    public KeyValueV3[] monotone_constraints;

    @API(help="Maximum absolute value of a leaf node prediction", level = API.Level.expert, gridable = true)
    public double max_abs_leafnode_pred;

    @API(help="Bandwidth (sigma) of Gaussian multiplicative noise ~N(1,sigma) for tree node predictions", level = API.Level.expert, gridable = true)
    public double pred_noise_bandwidth;

    @API(help="A set of allowed column interactions.", level= API.Level.expert)
    public String[][] interaction_constraints;

//    // TODO debug only, remove!
//    @API(help="Internal flag, use new version of histo tsk if set", level = API.Level.expert, gridable = false)
//    public boolean use_new_histo_tsk;
//    @API(help="Use with new histo task only! Internal flag, number of columns processed in parallel", level = API.Level.expert, gridable = false)
//    public int col_block_sz = 5;
//    @API(help="Use with new histo task only! Min threads to be run in parallel", level = API.Level.expert, gridable = false)
//    public int min_threads = -1;
//    @API(help="Use with new histo task only! Share histo (and use CAS) instead of making private copies", level = API.Level.expert, gridable = false)
//    public boolean shared_histo;
//    @API(help="Use with new histo task only! Access rows in order of the dataset, not in order of leafs ", level = API.Level.expert, gridable = false)
//    public boolean unordered;
  }
}
