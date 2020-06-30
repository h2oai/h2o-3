package hex.schemas;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.KeyValueV3;
import water.api.schemas3.ModelParametersSchemaV3;


public class XGBoostV3 extends ModelBuilderSchema<XGBoost,XGBoostV3,XGBoostV3.XGBoostParametersV3> {

  public static final class XGBoostParametersV3 extends ModelParametersSchemaV3<XGBoostParameters, XGBoostParametersV3> {
    static public String[] fields = new String[] {
        "model_id",
        "training_frame",
        "validation_frame",
        "nfolds",
        "keep_cross_validation_models",
        "keep_cross_validation_predictions",
        "keep_cross_validation_fold_assignment",
        "score_each_iteration",
        "fold_assignment",
        "fold_column",
        "response_column",
        "ignored_columns",
        "ignore_const_cols",
        "offset_column",
        "weights_column",
        "stopping_rounds",
        "stopping_metric",
        "stopping_tolerance",
        "max_runtime_secs",
        "seed",
        "distribution",
        "tweedie_power",
        "categorical_encoding",
        "quiet_mode",
        "checkpoint",
        "export_checkpoints_dir",

        // model specific
        "ntrees",
        "max_depth",
        "min_rows", "min_child_weight",
        "learn_rate", "eta",
//        "learn_rate_annealing", //disabled for now

        "sample_rate", "subsample",
        "col_sample_rate", "colsample_bylevel",
        "col_sample_rate_per_tree", "colsample_bytree",
        "colsample_bynode",
        "max_abs_leafnode_pred", "max_delta_step",

        "monotone_constraints",

        "score_tree_interval",
        "min_split_improvement", "gamma",

        //runtime
        "nthread",
        "save_matrix_directory",
        "build_tree_one_node",

        //platt scaling
        "calibrate_model",
        "calibration_frame",

        //lightgbm only
        "max_bins",
        "max_leaves",

        //dart
        "sample_type",
        "normalize_type",
        "rate_drop",
        "one_drop",
        "skip_drop",

        //xgboost only
        "tree_method",
        "grow_policy",
        "booster",
        "reg_lambda",
        "reg_alpha",
        "dmatrix_type",
        "backend",
        "gpu_id"
    };

    @API(help="(same as n_estimators) Number of trees.", gridable = true)
    public int ntrees;

    @API(help="Maximum tree depth.", gridable = true)
    public int max_depth;

    @API(help="(same as min_child_weight) Fewest allowed (weighted) observations in a leaf.", gridable = true)
    public double min_rows;
    @API(help="(same as min_rows) Fewest allowed (weighted) observations in a leaf.", gridable = true)
    public double min_child_weight;

    @API(help="(same as eta) Learning rate (from 0.0 to 1.0)", gridable = true)
    public double learn_rate;
    @API(help="(same as learn_rate) Learning rate (from 0.0 to 1.0)", gridable = true)
    public double eta;

//    @API(help="Scale the learning rate by this factor after each tree (e.g., 0.99 or 0.999) ", level = API.Level.secondary, gridable = true)
//    public double learn_rate_annealing;

    @API(help = "(same as subsample) Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
    public double sample_rate;
    @API(help = "(same as sample_rate) Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
    public double subsample;

    @API(help="(same as colsample_bylevel) Column sample rate (from 0.0 to 1.0)", gridable = true)
    public double col_sample_rate;
    @API(help="(same as col_sample_rate) Column sample rate (from 0.0 to 1.0)", gridable = true)
    public double colsample_bylevel;

    @API(help = "(same as colsample_bytree) Column sample rate per tree (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double col_sample_rate_per_tree;
    @API(help = "(same as col_sample_rate_per_tree) Column sample rate per tree (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double colsample_bytree;

    @API(help = "Column sample rate per tree node (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double colsample_bynode;

    @API(help = "A mapping representing monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint.", level = API.Level.secondary)
    public KeyValueV3[] monotone_constraints;

    @API(help="(same as max_delta_step) Maximum absolute value of a leaf node prediction", level = API.Level.expert, gridable = true)
    public float max_abs_leafnode_pred;
    @API(help="(same as max_abs_leafnode_pred) Maximum absolute value of a leaf node prediction", level = API.Level.expert, gridable = true)
    public float max_delta_step;

    @API(help="Score the model after every so many trees. Disabled if set to 0.", level = API.Level.secondary, gridable = false)
    public int score_tree_interval;

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    @API(help="(same as gamma) Minimum relative improvement in squared error reduction for a split to happen", level = API.Level.secondary, gridable = true)
    public float min_split_improvement;
    @API(help="(same as min_split_improvement) Minimum relative improvement in squared error reduction for a split to happen", level = API.Level.secondary, gridable = true)
    public float gamma;

    @API(help = "Number of parallel threads that can be used to run XGBoost. Cannot exceed H2O cluster limits (-nthreads parameter). Defaults to maximum available", level = API.Level.expert)
    public int nthread;

    @API(help="Run on one node only; no network overhead but fewer cpus used. Suitable for small datasets.", level = API.Level.expert, gridable = false)
    public boolean build_tree_one_node;

    @API(help = "Directory where to save matrices passed to XGBoost library. Useful for debugging.", level = API.Level.expert)
    public String save_matrix_directory;

    @API(help="Use Platt Scaling to calculate calibrated class probabilities. Calibration can provide more accurate estimates of class probabilities.", level = API.Level.expert)
    public boolean calibrate_model;

    @API(help="Calibration frame for Platt Scaling", level = API.Level.expert, direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 calibration_frame;

    @API(help = "For tree_method=hist only: maximum number of bins", level = API.Level.expert, gridable = true)
    public int max_bins;

    @API(help = "For tree_method=hist only: maximum number of leaves", level = API.Level.secondary, gridable = true)
    public int max_leaves;

    @API(help="Tree method", values = { "auto", "exact", "approx", "hist"}, level = API.Level.secondary, gridable = true)
    public XGBoostParameters.TreeMethod tree_method;

    @API(help="Grow policy - depthwise is standard GBM, lossguide is LightGBM", values = { "depthwise", "lossguide"}, level = API.Level.secondary, gridable = true)
    public XGBoostParameters.GrowPolicy grow_policy;

    @API(help="Booster type", values = { "gbtree", "gblinear", "dart"}, level = API.Level.expert, gridable = true)
    public XGBoostParameters.Booster booster;

    @API(help = "L2 regularization", level = API.Level.expert, gridable = true)
    public float reg_lambda;

    @API(help = "L1 regularization", level = API.Level.expert, gridable = true)
    public float reg_alpha;

    @API(help="Enable quiet mode", level = API.Level.expert, gridable = false)
    public boolean quiet_mode;

    @API(help="For booster=dart only: sample_type", values = { "uniform", "weighted"}, level = API.Level.expert, gridable = true)
    public XGBoostParameters.DartSampleType sample_type;

    @API(help="For booster=dart only: normalize_type", values = { "tree", "forest"}, level = API.Level.expert, gridable = true)
    public XGBoostParameters.DartNormalizeType normalize_type;

    @API(help="For booster=dart only: rate_drop (0..1)", level = API.Level.expert, gridable = true)
    public float rate_drop;

    @API(help="For booster=dart only: one_drop", level = API.Level.expert, gridable = true)
    public boolean one_drop;

    @API(help="For booster=dart only: skip_drop (0..1)", level = API.Level.expert, gridable = true)
    public float skip_drop;

    @API(help="Type of DMatrix. For sparse, NAs and 0 are treated equally.", values = { "auto", "dense", "sparse" }, level = API.Level.secondary, gridable = true)
    public XGBoostParameters.DMatrixType dmatrix_type;

    @API(help="Backend. By default (auto), a GPU is used if available.", values = { "auto", "gpu", "cpu" }, level = API.Level.expert, gridable = true)
    public XGBoostParameters.Backend backend;

    @API(help="Which GPU to use. ", level = API.Level.expert, gridable = false)
    public int gpu_id;
  }
}
