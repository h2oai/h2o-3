package hex.schemas;

import hex.anovaglm.ANOVAGLM;
import hex.anovaglm.ANOVAGLMModel;
import hex.glm.GLMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class ANOVAGLMV3 extends ModelBuilderSchema<ANOVAGLM, ANOVAGLMV3, ANOVAGLMV3.ANOVAGLMParametersV3> {

  public static final class ANOVAGLMParametersV3 extends ModelParametersSchemaV3<ANOVAGLMModel.ANOVAGLMParameters,
          ANOVAGLMParametersV3> {
    public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "seed",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "score_each_iteration",
            "offset_column",
            "weights_column",
            "family",
            "tweedie_variance_power",
            "tweedie_link_power",
            "theta", // equals to 1/r and should be > 0 and <=1, used by negative binomial
            "solver",
            "missing_values_handling",
            "plug_values",
            "compute_p_values",
            "standardize",
            "non_negative",
            "max_iterations",
            "link",
            "prior",
            "alpha",
            "lambda",
            "lambda_search",
            "stopping_rounds",
            "stopping_metric",
            "early_stopping",
            "stopping_tolerance",
            "balance_classes",
            "class_sampling_factors",
            "max_after_balance_size",
            "max_runtime_secs",
            "save_transformed_framekeys",
            "highest_interaction_term",
            "nparallelism",
            "type" // GLM SS Type, only support 3 right now
    };

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    @API(help = "Standardize numeric columns to have zero mean and unit variance", level = API.Level.critical)
    public boolean standardize;

    // Input fields
    @API(help = "Family. Use binomial for classification with logistic regression, others are for regression problems.",
            values = {"AUTO", "gaussian", "binomial", "fractionalbinomial", "quasibinomial", "poisson", "gamma",
                    "tweedie", "negativebinomial"}, level = API.Level.critical)
    public GLMModel.GLMParameters.Family family;

    @API(help = "Tweedie variance power", level = API.Level.critical, gridable = true)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level = API.Level.critical, gridable = true)
    public double tweedie_link_power;

    @API(help = "Theta", level = API.Level.critical, gridable = true)
    public double theta; // used by negtaive binomial distribution family

    @API(help = "Distribution of regularization between the L1 (Lasso) and L2 (Ridge) penalties." +
            " A value of 1 for alpha represents Lasso regression, a value of 0 produces Ridge regression, and " +
            "anything in between specifies the amount of mixing between the two. Default value of alpha is 0 when" +
            " SOLVER = 'L-BFGS'; 0.5 otherwise.", level = API.Level.critical, gridable = true)
    public double[] alpha;

    @API(help = "Regularization strength", required = false, level = API.Level.critical, gridable = true)
    public double[] lambda;
    
    @API(help = "Use lambda search starting at lambda max, given lambda is then interpreted as lambda min", 
            level = API.Level.critical)
    public boolean lambda_search;

    @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on problems" +
            " with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales better for datasets" +
            " with many columns.", values = {"AUTO", "IRLSM", "L_BFGS","COORDINATE_DESCENT_NAIVE",
            "COORDINATE_DESCENT", "GRADIENT_DESCENT_LH", "GRADIENT_DESCENT_SQERR"}, level = API.Level.critical)
    public GLMModel.GLMParameters.Solver solver;

    @API(help = "Handling of missing values. Either MeanImputation, Skip or PlugValues.", values = { "MeanImputation",
            "Skip", "PlugValues" }, level = API.Level.expert, direction=API.Direction.INOUT, gridable = true)
    public GLMModel.GLMParameters.MissingValuesHandling missing_values_handling;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the" +
            " training/validation frame, use with conjunction missing_values_handling = PlugValues)",
            direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 plug_values;

    @API(help = "Restrict coefficients (not intercept) to be non-negative")
    public boolean non_negative;

    @API(help="Request p-values computation, p-values work only with IRLSM solver and no regularization",
            level = API.Level.secondary, direction = API.Direction.INPUT)
    public boolean compute_p_values; // _remove_collinear_columns

    @API(help = "Maximum number of iterations", level = API.Level.secondary)
    public int max_iterations;

    @API(help = "Link function.", level = API.Level.secondary, values = {"family_default", "identity", "logit", "log",
            "inverse", "tweedie", "ologit"}) //"oprobit", "ologlog": will be supported.
    public GLMModel.GLMParameters.Link link;

    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled and" +
            " the mean of response does not reflect reality.", level = API.Level.expert)
    public double prior;

    // dead unused args, formely inherited from supervised model schema
    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).",
            level = API.Level.secondary, direction = API.Direction.INOUT)
    public boolean balance_classes;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling" +
            " factors will be automatically computed to obtain class balance during training. Requires " +
            "balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). " +
            "Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT)
    public float max_after_balance_size;

    @API(help = "Limit the number of interaction terms, if 2 means interaction between 2 columns only, 3 for three" +
            " columns and so on...  Default to 2.", level = API.Level.critical)
    public int highest_interaction_term;  // GLM SS Type, only support 3

    @API(help = "Refer to the SS type 1, 2, 3, or 4.  We are currently only supporting 3", level = API.Level.critical)
    public int type;  // GLM SS Type, only support 3

    @API(help="Stop early when there is no more relative improvement on train or validation (if provided).")
    public boolean early_stopping;

    @API(help="true to save the keys of transformed predictors and interaction column.")
    public boolean save_transformed_framekeys;

    @API(help="Number of models to build in parallel.  Default to 4.  Adjust according to your system.")
    public int nparallelism;
  }
}
