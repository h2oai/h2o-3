package hex.schemas;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;


public class XGBoostV3 extends ModelBuilderSchema<XGBoost,XGBoostV3,XGBoostV3.XGBoostParametersV3> {

  public static final class XGBoostParametersV3 extends ModelParametersSchemaV3<XGBoostParameters, XGBoostParametersV3> {
    static public String[] fields = new String[] {
        "model_id",
        "training_frame",
        "validation_frame",
        "nfolds",
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

        // model specific
        "ntrees",
        "max_depth",
        "min_rows",
        "learn_rate",
        "sample_rate",
        "col_sample_rate",
        "col_sample_rate_per_tree",
        "max_abs_leafnode_pred",
        "score_tree_interval",
        "min_split_improvement",

        //lightgbm only
        "max_bin",
        "num_leaves",
        "min_sum_hessian_in_leaf",
        "min_data_in_leaf",

        //xgboost only
        "tree_method",
        "grow_policy",
        "booster",
        "lambda",
        "alpha"
    };

    @API(help="Number of trees.", gridable = true)
    public int ntrees;

    @API(help="Maximum tree depth.", gridable = true)
    public int max_depth;

    @API(help="Fewest allowed (weighted) observations in a leaf.", gridable = true)
    public double min_rows;

    @API(help="Learning rate (from 0.0 to 1.0)", gridable = true)
    public double learn_rate;

    @API(help = "Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
    public double sample_rate;

    @API(help="Column sample rate (from 0.0 to 1.0)", gridable = true)
    public double col_sample_rate;

    @API(help = "Column sample rate per tree (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double col_sample_rate_per_tree;

    @API(help="Maximum absolute value of a leaf node prediction", level = API.Level.expert, gridable = true)
    public float max_abs_leafnode_pred;

    @API(help="Score the model after every so many trees. Disabled if set to 0.", level = API.Level.secondary, gridable = false)
    public int score_tree_interval;

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    @API(help="Minimum relative improvement in squared error reduction for a split to happen", level = API.Level.secondary, gridable = true)
    public float min_split_improvement;

    @API(help = "For tree_method=hist only: maximum number of bins", level = API.Level.expert, gridable = true)
    public int max_bin;

    @API(help = "For tree_method=hist only: maximum number of leaves", level = API.Level.secondary, gridable = true)
    public int num_leaves;

    @API(help = "For tree_method=hist only: the mininum sum of hessian in a leaf to keep splitting", level = API.Level.expert, gridable = true)
    public float min_sum_hessian_in_leaf;

    @API(help = "For tree_method=hist only: the mininum data in a leaf to keep splitting", level = API.Level.expert, gridable = true)
    public float min_data_in_leaf;

    @API(help="Tree method", values = { "auto", "exact", "approx", "hist"}, level = API.Level.secondary, gridable = true)
    public XGBoostParameters.TreeMethod tree_method;

    @API(help="Grow policy - depthwise is standard GBM, lossguide is LightGBM", values = { "depthwise", "lossguide"}, level = API.Level.secondary, gridable = true)
    public XGBoostParameters.GrowPolicy grow_policy;

    @API(help="Booster type", values = { "gbtree", "gblinear"}, level = API.Level.expert, gridable = true)
    public XGBoostParameters.Booster booster;

    @API(help = "L2 regularization", level = API.Level.expert, gridable = true)
    public float lambda;

    @API(help = "L1 regularization", level = API.Level.expert, gridable = true)
    public float alpha;

    @API(help="Missing Value Handling", values = { "mean_imputation", "skip"}, level = API.Level.expert, gridable = true)
    public XGBoostParameters.MissingValuesHandling missing_values_handling;

    @API(help="Enable quiet mode", level = API.Level.expert, gridable = false)
    public boolean quiet_mode;
  }
}
