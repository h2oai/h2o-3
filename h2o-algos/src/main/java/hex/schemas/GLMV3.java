package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.KeyV3.FrameKeyV3;
import water.api.ModelParametersSchema;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV3 extends ModelBuilderSchema<GLM,GLMV3,GLMV3.GLMParametersV3> {

  public static final class GLMParametersV3 extends ModelParametersSchema<GLMParameters, GLMParametersV3> {
    static public String[] fields = new String[]{
            "model_id",
            "training_frame",
            "validation_frame",
            "nfolds",
            "keep_cross_validation_predictions",
            "fold_assignment",
            "fold_column",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "score_each_iteration",
            "offset_column",
            "weights_column",
            "family",
            "tweedie_variance_power",
            "tweedie_link_power",
            "solver",
            "alpha",
            "lambda",
            "lambda_search",
            "nlambdas",
            "standardize",
            "intercept",
            "non_negative",
            "max_iterations",
            "objective_epsilon",
            "beta_epsilon",
            "gradient_epsilon",
            "link",
            "prior",
            "lambda_min_ratio",
            "beta_constraints",
            "max_active_predictors",
            // dead unused args forced here by backwards compatibility, remove in V4
            "balance_classes",
            "class_sampling_factors",
            "max_after_balance_size",
            "max_confusion_matrix_size",
            "max_hit_ratio_k",
    };

    // Input fields
    @API(help = "Family. Use binomial for classification with logistic regression, others are for regression problems.", values = {"gaussian", "binomial","multinomial", "poisson", "gamma", "tweedie"}, level = Level.critical)
    // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "Tweedie variance power", level = Level.critical, gridable = true)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level = Level.critical, gridable = true)
    public double tweedie_link_power;

    @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on problems with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales better for datasets with many columns. Coordinate descent is experimental (beta).", values = {"AUTO", "IRLSM", "L_BFGS","COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT"}, level = Level.critical)
    public Solver solver;

    @API(help = "distribution of regularization between L1 and L2.", level = Level.critical, gridable = true)
    public double[] alpha;

    @API(help = "regularization strength", required = false, level = Level.critical, gridable = true)
    public double[] lambda;

    @API(help = "use lambda search starting at lambda max, given lambda is then interpreted as lambda min", level = Level.critical)
    public boolean lambda_search;

    @API(help = "number of lambdas to be used in a search", level = Level.critical)
    public int nlambdas;

    @API(help = "Standardize numeric columns to have zero mean and unit variance", level = Level.critical)
    public boolean standardize;

    @API(help = "Restrict coefficients (not intercept) to be non-negative")
    public boolean non_negative;

    @API(help = "Maximum number of iterations", level = Level.secondary)
    public int max_iterations;

    @API(help = "converge if  beta changes less (using L-infinity norm) than beta esilon, ONLY applies to IRLSM solver ", level = Level.expert)
    public double beta_epsilon;

    @API(help = "converge if  objective value changes less than this", level = Level.expert)
    public double objective_epsilon;

    @API(help = "converge if  objective changes less (using L-infinity norm) than this, ONLY applies to L-BFGS solver", level = Level.expert)
    public double gradient_epsilon;

    @API(help="likelihood divider in objective value computation, default is 1/nobs")
    public double obj_reg;

    @API(help = "", level = Level.secondary, values = {"family_default", "identity", "logit", "log", "inverse", "tweedie"})
    public GLMParameters.Link link;

    @API(help="include constant term in the model", level = Level.expert)
    public boolean intercept;

//    @API(help = "Tweedie variance power", level = Level.secondary)
//    public double tweedie_variance_power;
//
//    @API(help = "Tweedie link power", level = Level.secondary)
//    public double tweedie_link_power;

    @API(help = "prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.", level = Level.expert)
    public double prior;

    @API(help = "min lambda used in lambda search, specified as a ratio of lambda_max", level = Level.expert)
    public double lambda_min_ratio;

    @API(help = "beta constraints", direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
    public FrameKeyV3 beta_constraints;

    @API(help="Maximum number of active predictors during computation. Use as a stopping criterium to prevent expensive model building with many predictors.", direction = Direction.INPUT, level = Level.expert)
    public int max_active_predictors = -1;
    
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
    @API(help = "Maximum size (# classes) for confusion matrices to be printed in the Logs", level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_confusion_matrix_size;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public int max_hit_ratio_k;

    /////////////////////


  }
}
