package hex.schemas;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel.NaiveBayesParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class NaiveBayesV3 extends ModelBuilderSchema<NaiveBayes,NaiveBayesV3,NaiveBayesV3.NaiveBayesParametersV3> {
  public static final class NaiveBayesParametersV3 extends ModelParametersSchemaV3<NaiveBayesParameters, NaiveBayesParametersV3> {
    static public String[] fields = new String[]{
				"model_id",
        "nfolds",
        "seed",
        "fold_assignment",
        "fold_column",
        "keep_cross_validation_models",
        "keep_cross_validation_predictions",
        "keep_cross_validation_fold_assignment",
        "training_frame",
        "validation_frame",
        "response_column",
        "ignored_columns",
        "ignore_const_cols",
        "score_each_iteration",
        "balance_classes",
        "class_sampling_factors",
        "max_after_balance_size",
        "max_confusion_matrix_size",
        "max_hit_ratio_k",
        "laplace",
        "min_sdev",
        "eps_sdev",
        "min_prob",
        "eps_prob",
        "compute_metrics",
        "max_runtime_secs",
        "export_checkpoints_dir"
		};

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
    @API(help = "[Deprecated] Maximum size (# classes) for confusion matrices to be printed in the Logs", level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_confusion_matrix_size;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public int max_hit_ratio_k;

    //

    @API(help = "Laplace smoothing parameter", gridable = true)
    public double laplace;

    @API(help = "Min. standard deviation to use for observations with not enough data", gridable = true)
    public double min_sdev;

    @API(help = "Cutoff below which standard deviation is replaced with min_sdev", gridable = true)
    public double eps_sdev;

    @API(help = "Min. probability to use for observations with not enough data", gridable = true)
    public double min_prob;

    @API(help = "Cutoff below which probability is replaced with min_prob", gridable = true)
    public double eps_prob;

    @API(help = "Compute metrics on training data", gridable = true)
    public boolean compute_metrics;

    @API(help = "Seed for pseudo random number generator (only used for cross-validation and fold_assignment=\"Random\" or \"AUTO\")", level = API.Level.expert, direction=API.Direction.INOUT, gridable = true)
    public long seed;
  }
}
