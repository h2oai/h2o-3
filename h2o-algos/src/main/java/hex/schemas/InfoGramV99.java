package hex.schemas;

import hex.infogram.InfoGram;
import hex.infogram.InfoGramModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

import static hex.infogram.InfoGramModel.InfoGramParameter.Algorithm;

public class InfoGramV99 extends ModelBuilderSchema<InfoGram, InfoGramV99, InfoGramV99.INFOGRAMParametersV99> {
  public static final class INFOGRAMParametersV99 extends ModelParametersSchemaV3<InfoGramModel.InfoGramParameter, InfoGramV99.INFOGRAMParametersV99> {
    public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "validation_frame",
            "seed",
            "keep_cross_validation_models",
            "keep_cross_validation_predictions",
            "keep_cross_validation_fold_assignment",
            "fold_assignment",
            "fold_column",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "score_each_iteration",
            "offset_column",
            "weights_column",
            "early_stopping",
            "standardize",
            "non-negative",
            "missing_values_handling",
            "plug_values",
            "max_iterations",
            "stopping_rounds",
            "stopping_metric",
            "stopping_tolerance",
            // dead unused args forced here by backwards compatibility, remove in V4
            "balance_classes",
            "class_sampling_factors",
            "max_after_balance_size",
            "max_confusion_matrix_size",
            "max_runtime_secs",
            "custom_metric_func",
            "auc_type",
            // new parameters for INFOGRAMs only
            "infogram_algorithm", // choose algo and parameter to generate infogram
            "infogram_algorithm_params",
            "model_algorithm",    // choose algo and parameter to generate final model
            "model_algorithm_params",
            "sensitive_attributes",
            "conditional_info_threshold",
            "varimp_threshold",
            "data_fraction",
            "parallelism",
            "ntop",
            "pval"
    };

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    // Input fields
    @API(help = "Standardize numeric columns to have zero mean and unit variance", level = API.Level.critical)
    public boolean standardize;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the training/validation frame, use with conjunction missing_values_handling = PlugValues)", direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 plug_values;

    @API(help = "Restrict coefficients (not intercept) to be non-negative")
    public boolean non_negative;

    @API(help = "Maximum number of iterations", level = API.Level.secondary)
    public int max_iterations;

    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.", level = API.Level.expert)
    public double prior;

    // dead unused args, formely inherited from supervised model schema
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
    
    @API(help = "Machine learning algorithm chosen to build the infogram.  AUTO default to GBM", values={"AUTO", 
            "deeplearning", "drf", "gbm", "glm"}, level = API.Level.expert, direction = API.Direction.INOUT, gridable=true)
    public Algorithm infogram_algorithm;
    
    @API(help = "parameters specified to the chosen algorithm can be passed to infogram using algorithm_params", 
            level = API.Level.expert, gridable=true)
    public String infogram_algorithm_params;

    @API(help = "Machine learning algorithm chosen to build the final model.  AUTO default to GBM", values={"AUTO",
            "deeplearning", "drf", "gbm", "glm"}, level = API.Level.critical, direction = API.Direction.INOUT, gridable=true)
    public Algorithm model_algorithm;

    @API(help = "parameters specified to the chosen final algorithm", level = API.Level.secondary, gridable=true)
    public String model_algorithm_params;
    
    @API(help = "predictors that are to be excluded from model due to them being discriminatory or inappropriate for" +
            " whatever reason.", level = API.Level.secondary, gridable=true)
    public String[] sensitive_attributes;
    
    @API(help = "conditional information threshold between 0 and 1 that is used to decide whether a predictor's " +
            "conditional information is high enough.  Default to 0.1", level = API.Level.secondary, gridable = true)
    public double conditional_info_threshold;
    
    @API(help = "variable importance threshold between 0 and 1 that is used to decide whether a predictor's relevance" +
            " level is high enough.  Default to 0.1", level = API.Level.secondary, gridable = true)
    public double varimp_threshod;
    
    @API(help = "fraction of training frame to use to build the infogram model.  Default to 1.0", 
            level = API.Level.secondary, gridable = true)
    public double data_fraction;

    @API(help = "number of models to build in parallel.  Default to 0.0 which is adaptive to the system capability",
            level = API.Level.secondary, gridable = true)
    public int parallelism;

    @API(help = "number of top k variables to consider based on the varimp.  Default to 0.0 which is to consider" +
            " all predictors",
            level = API.Level.secondary, gridable = true)
    public int ntop;

    @API(help = "If true will calculate the p-value. Default to false",
            level = API.Level.secondary, gridable = false)
    public int pval;
  }
}
