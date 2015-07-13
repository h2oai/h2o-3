package hex.schemas;

import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import water.api.*;
import water.api.FrameV3.ColSpecifierV3;

public class SharedTreeV3<B extends SharedTree, S extends SharedTreeV3<B,S,P>, P extends SharedTreeV3.SharedTreeParametersV3> extends ModelBuilderSchema<B,S,P> {

  public static class SharedTreeParametersV3<P extends SharedTreeParameters, S extends SharedTreeParametersV3<P, S>> extends ModelParametersSchema<P, S> {
    // supervised Schema
    // TODO: pass these as a new helper class that contains frame and vec; right now we have no automagic way to
    // know which frame a Vec name corresponds to, so there's hardwired logic in the adaptor which knows that these
    // column names are related to training_frame.
    @API(help = "Response column", is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns"}, direction = API.Direction.INOUT)
    public ColSpecifierV3 response_column;

    @API(help = "Column with observation weights", is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns","response_column"}, direction = API.Direction.INOUT)
    public ColSpecifierV3 weights_column;

    @API(help = "Offset column", is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns","response_column", "weights_column"}, direction = API.Direction.INOUT)
    public ColSpecifierV3 offset_column;

  /*Imbalanced Classes*/
    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).", level = API.Level.secondary, direction = API.Direction.INOUT)
    public boolean balance_classes;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT)
    public float max_after_balance_size;

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.  */
    @API(help = "Maximum size (# classes) for confusion matrices to be printed in the Logs", level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_confusion_matrix_size;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public int max_hit_ratio_k;

    @API(help="Number of trees.", gridable = true)
    public int ntrees;

    @API(help="Maximum tree depth.", gridable = true)
    public int max_depth;

    @API(help="Fewest allowed (weighted) observations in a leaf (in R called 'nodesize').", gridable = true)
    public double min_rows;

    @API(help="For numerical columns (real/int), build a histogram of this many bins, then split at the best point", gridable = true)
    public int nbins;

    @API(help="For categorical columns (enum), build a histogram of this many bins, then split at the best point. Higher values can lead to more overfitting.", gridable = true)
    public int nbins_cats;

    @API(help="Stop making trees when the R^2 metric equals or exceeds this", level = API.Level.secondary)
    public double r2_stopping;

    @API(help = "Seed for pseudo random number generator (if applicable)")
    public long seed;

    @API(help="Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.", level = API.Level.secondary)
    public boolean build_tree_one_node;
  }
}
