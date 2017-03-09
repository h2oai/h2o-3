package hex.schemas;

import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class SharedTreeV3<B extends SharedTree, S extends SharedTreeV3<B,S,P>, P extends SharedTreeV3.SharedTreeParametersV3> extends ModelBuilderSchema<B,S,P> {

  public static class SharedTreeParametersV3<P extends SharedTreeParameters, S extends SharedTreeParametersV3<P, S>> extends ModelParametersSchemaV3<P, S> {
  /*Imbalanced Classes*/
    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).", level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true)
    public boolean balance_classes;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public float max_after_balance_size;

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.  */
    @API(help = "[Deprecated] Maximum size (# classes) for confusion matrices to be printed in the Logs", level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_confusion_matrix_size;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = false)
    public int max_hit_ratio_k;

    @API(help="Number of trees.", gridable = true)
    public int ntrees;

    @API(help="Maximum tree depth.", gridable = true)
    public int max_depth;

    @API(help="Fewest allowed (weighted) observations in a leaf.", gridable = true)
    public double min_rows;

    @API(help="For numerical columns (real/int), build a histogram of (at least) this many bins, then split at the best point", gridable = true)
    public int nbins;

    @API(help = "For numerical columns (real/int), build a histogram of (at most) this many bins at the root level, then decrease by factor of two per level", level = API.Level.secondary, gridable = true)
    public int nbins_top_level;

    @API(help="For categorical columns (factors), build a histogram of this many bins, then split at the best point. Higher values can lead to more overfitting.", level = API.Level.secondary, gridable = true)
    public int nbins_cats;

    @API(help="r2_stopping is no longer supported and will be ignored if set - please use stopping_rounds, stopping_metric and stopping_tolerance instead. Previous version of H2O would stop making trees when the R^2 metric equals or exceeds this", level = API.Level.secondary, gridable = true)
    public double r2_stopping;

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    @API(help="Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.", level = API.Level.expert, gridable = false)
    public boolean build_tree_one_node;

    @API(help = "Row sample rate per tree (from 0.0 to 1.0)", gridable = true)
    public double sample_rate;

    @API(help = "Row sample rate per tree per class (from 0.0 to 1.0)", level = API.Level.expert, gridable = true)
    public double[] sample_rate_per_class;

    @API(help = "Column sample rate per tree (from 0.0 to 1.0)", level = API.Level.secondary, gridable = true)
    public double col_sample_rate_per_tree;

    @API(help = "Relative change of the column sampling rate for every level (from 0.0 to 2.0)", level = API.Level.expert, gridable = true)
    public double col_sample_rate_change_per_level;

    @API(help="Score the model after every so many trees. Disabled if set to 0.", level = API.Level.secondary, gridable = false)
    public int score_tree_interval;

    @API(help="Minimum relative improvement in squared error reduction for a split to happen", level = API.Level.secondary, gridable = true)
    public double min_split_improvement;

    @API(help="What type of histogram to use for finding optimal split points", values = { "AUTO", "UniformAdaptive", "Random", "QuantilesGlobal", "RoundRobin"}, level = API.Level.secondary, gridable = true)
    public SharedTreeParameters.HistogramType histogram_type;

    @API(help="L1 regularization. Higher values will make the model more conservative.", level = API.Level.secondary, gridable = true)
    public double reg_alpha;

    @API(help="L2 regularization. Higher values will make the model more conservative.", level = API.Level.secondary, gridable = true)
    public double reg_lambda;
  }
}
