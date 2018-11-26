package hex.schemas;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.StringPairV3;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV3 extends ModelBuilderSchema<GLM,GLMV3,GLMV3.GLMParametersV3> {

  public static final class GLMParametersV3 extends ModelParametersSchemaV3<GLMParameters, GLMParametersV3> {
     public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "validation_frame",
            "nfolds",
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
            "family",
            "tweedie_variance_power",
            "tweedie_link_power",
            "solver",
            "alpha",
            "lambda",
            "lambda_search",
            "early_stopping",
            "nlambdas",
            "standardize",
            "missing_values_handling",
            "compute_p_values",
            "remove_collinear_columns",
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
            "interactions",
            "interaction_pairs",
            "obj_reg",
           "export_checkpoints_dir",
            // dead unused args forced here by backwards compatibility, remove in V4
            "balance_classes",
            "class_sampling_factors",
            "max_after_balance_size",
            "max_confusion_matrix_size",
            "max_hit_ratio_k",
            "max_runtime_secs",
            "custom_metric_func"
    };

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    // Input fields
    @API(help = "Family. Use binomial for classification with logistic regression, others are for regression problems.", values = {"gaussian", "binomial","quasibinomial","ordinal", "multinomial", "poisson", "gamma", "tweedie"}, level = Level.critical)
    // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "Tweedie variance power", level = Level.critical, gridable = true)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level = Level.critical, gridable = true)
    public double tweedie_link_power;

    @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on problems with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales better for datasets with many columns.", values = {"AUTO", "IRLSM", "L_BFGS","COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT", "GRADIENT_DESCENT_LH", "GRADIENT_DESCENT_SQERR"}, level = Level.critical)
    public Solver solver;

    @API(help = "Distribution of regularization between the L1 (Lasso) and L2 (Ridge) penalties. A value of 1 for alpha represents Lasso regression, a value of 0 produces Ridge regression, and anything in between specifies the amount of mixing between the two. Default value of alpha is 0 when SOLVER = 'L-BFGS'; 0.5 otherwise.", level = Level.critical, gridable = true)
    public double[] alpha;

    @API(help = "Regularization strength", required = false, level = Level.critical, gridable = true)
    public double[] lambda;

    @API(help = "Use lambda search starting at lambda max, given lambda is then interpreted as lambda min", level = Level.critical)
    public boolean lambda_search;

    @API(help="Stop early when there is no more relative improvement on train or validation (if provided)")
    public boolean early_stopping;

    @API(help = "Number of lambdas to be used in a search." +
    " Default indicates: If alpha is zero, with lambda search" +
    " set to True, the value of nlamdas is set to 30 (fewer lambdas" +
    " are needed for ridge regression) otherwise it is set to 100.", level = Level.critical)
    public int nlambdas;

    @API(help = "Standardize numeric columns to have zero mean and unit variance", level = Level.critical)
    public boolean standardize;

    @API(help = "Handling of missing values. Either MeanImputation or Skip.", values = { "MeanImputation", "Skip" }, level = API.Level.expert, direction=API.Direction.INOUT, gridable = true)
    public DeepLearningParameters.MissingValuesHandling missing_values_handling;

    @API(help = "Restrict coefficients (not intercept) to be non-negative")
    public boolean non_negative;

    @API(help = "Maximum number of iterations", level = Level.secondary)
    public int max_iterations;

    @API(help = "Converge if  beta changes less (using L-infinity norm) than beta esilon, ONLY applies to IRLSM solver ", level = Level.expert)
    public double beta_epsilon;

    @API(help = "Converge if  objective value changes less than this."+ " Default indicates: If lambda_search"+
    " is set to True the value of objective_epsilon is set to .0001. If the lambda_search is set to False and" +
    " lambda is equal to zero, the value of objective_epsilon is set to .000001, for any other value of lambda the" +
    " default value of objective_epsilon is set to .0001.", level = Level.expert)
    public double objective_epsilon;

    @API(help = "Converge if  objective changes less (using L-infinity norm) than this, ONLY applies to L-BFGS solver."+
    " Default indicates: If lambda_search is set to False and lambda is equal to zero, the default value" +
    " of gradient_epsilon is equal to .000001, otherwise the default value is .0001. If lambda_search is set to True," +
    " the conditional values above are 1E-8 and 1E-6 respectively.", level = Level.expert)
    public double gradient_epsilon;

    @API(help="Likelihood divider in objective value computation, default is 1/nobs")
    public double obj_reg;

    @API(help = "", level = Level.secondary, values = {"family_default", "identity", "logit", "log", "inverse",
            "tweedie", "ologit", "oprobit", "ologlog"})
    public GLMParameters.Link link;

    @API(help="Include constant term in the model", level = Level.expert)
    public boolean intercept;

//    @API(help = "Tweedie variance power", level = Level.secondary)
//    public double tweedie_variance_power;
//
//    @API(help = "Tweedie link power", level = Level.secondary)
//    public double tweedie_link_power;

    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.", level = Level.expert)
    public double prior;

    @API(help = "Minimum lambda used in lambda search, specified as a ratio of lambda_max (the smallest lambda that drives all coefficients to zero)." +
    " Default indicates: if the number of observations is greater than the number of variables, then lambda_min_ratio" +
    " is set to 0.0001; if the number of observations is less than the number of variables, then lambda_min_ratio" +
    " is set to 0.01.", level = Level.expert)
    public double lambda_min_ratio;

    @API(help = "Beta constraints", direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
    public FrameKeyV3 beta_constraints;

    @API(help="Maximum number of active predictors during computation. Use as a stopping criterion" +
    " to prevent expensive model building with many predictors." + " Default indicates: If the IRLSM solver is used," +
    " the value of max_active_predictors is set to 5000 otherwise it is set to 100000000.", direction = Direction.INPUT, level = Level.expert)
    public int max_active_predictors = -1;

    @API(help="A list of predictor column indices to interact. All pairwise combinations will be computed for the list.", direction=Direction.INPUT, level=Level.expert)
    public String[] interactions;

    @API(help="A list of pairwise (first order) column interactions.", direction=Direction.INPUT, level=Level.expert)
    public StringPairV3[] interaction_pairs;

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

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(help = "Maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public int max_hit_ratio_k;

    @API(help="Request p-values computation, p-values work only with IRLSM solver and no regularization", level = Level.secondary, direction = Direction.INPUT)
    public boolean compute_p_values; // _remove_collinear_columns

    @API(help="In case of linearly dependent columns, remove some of the dependent columns", level = Level.secondary, direction = Direction.INPUT)
    public boolean remove_collinear_columns; // _remove_collinear_columns

    /////////////////////
  }
}
