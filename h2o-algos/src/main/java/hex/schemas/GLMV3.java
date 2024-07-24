package hex.schemas;

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
    public static final String[] fields = new String[]{
            "model_id",
            "training_frame",
            "validation_frame",
            "nfolds",
            "checkpoint",
            "export_checkpoints_dir",
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
            "score_iteration_interval",
            "offset_column",
            "weights_column",
            "family",
            "tweedie_variance_power",
            "tweedie_link_power",
            "theta", // equals to 1/r and should be > 0 and <=1, used by negative binomial
            "solver",
            "alpha",
            "lambda",
            "lambda_search",
            "early_stopping",
            "nlambdas",
            "standardize",
            "missing_values_handling",
            "plug_values",
            "compute_p_values",
            "dispersion_parameter_method",
            "init_dispersion_parameter",
            "remove_collinear_columns",
            "intercept",
            "non_negative",
            "max_iterations",
            "objective_epsilon",
            "beta_epsilon",
            "gradient_epsilon",
            "link",
            "startval",  // initial starting values for coefficients, double array
            "calc_like", // true will return likelihood function value
            "prior",
            "cold_start", // if true, will start GLM model from initial values and conditions
            "lambda_min_ratio",
            "beta_constraints",
            "max_active_predictors",
            "interactions",
            "interaction_pairs",
            "obj_reg",
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
            "generate_scoring_history",
            "auc_type",
            "dispersion_epsilon",
            "tweedie_epsilon",
            "max_iterations_dispersion",
            "build_null_model",
            "fix_dispersion_parameter",
            "generate_variable_inflation_factors",
            "fix_tweedie_variance_power",
            "dispersion_learning_rate",
            "influence",
            "gainslift_bins",
            "linear_constraints",
            "init_optimal_glm",     // default to true
            "separate_linear_beta", // default to false
            "constraint_eta0",      // default to 0.1258925
            "constraint_tau",       // default to 10
            "constraint_alpha",     // default to 0.1
            "constraint_beta",      // default to 0.9
            "constraint_c0",        // default to 10
    };

    @API(help = "Seed for pseudo random number generator (if applicable).", gridable = true)
    public long seed;

    // Input fields
    @API(help = "Family. Use binomial for classification with logistic regression, others are for regression problems.",
            values = {"AUTO", "gaussian", "binomial", "fractionalbinomial", "quasibinomial", "ordinal", "multinomial",
                    "poisson", "gamma", "tweedie", "negativebinomial"}, level = Level.critical)
    // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "Tweedie variance power", level = Level.critical, gridable = true)
    public double tweedie_variance_power;

    @API(help = "Dispersion learning rate is only valid for tweedie family dispersion parameter estimation using ml. " +
            "It must be > 0.  This controls how much the dispersion parameter estimate is to be changed when the" +
            " calculated loglikelihood actually decreases with the new dispersion.  In this case, instead of setting" +
            " new dispersion = dispersion + change, we set new dispersion = dispersion + dispersion_learning_rate * change. " +
            "Defaults to 0.5.", level = Level.expert, gridable = true)
    public double dispersion_learning_rate;

    @API(help = "Tweedie link power.", level = Level.critical, gridable = true)
    public double tweedie_link_power;

    @API(help = "Theta", level = Level.critical, gridable = true)
    public double theta; // used by negtaive binomial distribution family

    @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on problems" +
            " with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales better for datasets" +
            " with many columns.", values = {"AUTO", "IRLSM", "L_BFGS","COORDINATE_DESCENT_NAIVE", 
            "COORDINATE_DESCENT", "GRADIENT_DESCENT_LH", "GRADIENT_DESCENT_SQERR"}, level = Level.critical)
    public Solver solver;

    @API(help = "Distribution of regularization between the L1 (Lasso) and L2 (Ridge) penalties. A value of 1 for " +
            "alpha represents Lasso regression, a value of 0 produces Ridge regression, and anything in between " +
            "specifies the amount of mixing between the two. Default value of alpha is 0 when SOLVER = 'L-BFGS'; 0.5" +
            " otherwise.", level = Level.critical, gridable = true)
    public double[] alpha;

    @API(help = "Regularization strength", required = false, level = Level.critical, gridable = true)
    public double[] lambda;

    @API(help = "Use lambda search starting at lambda max, given lambda is then interpreted as lambda min.",
            level = Level.critical)
    public boolean lambda_search;

    @API(help="Stop early when there is no more relative improvement on train or validation (if provided).")
    public boolean early_stopping;

    @API(help = "Number of lambdas to be used in a search." +
    " Default indicates: If alpha is zero, with lambda search" +
    " set to True, the value of nlamdas is set to 30 (fewer lambdas" +
    " are needed for ridge regression) otherwise it is set to 100.", level = Level.critical)
    public int nlambdas;
    
    @API(help = "Perform scoring for every score_iteration_interval iterations.", level = Level.secondary)
    public int score_iteration_interval;

    @API(help = "Standardize numeric columns to have zero mean and unit variance.", level = Level.critical,
            gridable = true)
    public boolean standardize;

    @API(help = "Only applicable to multiple alpha/lambda values.  If false, build the next model for next set of " +
            "alpha/lambda values starting from the values provided by current model.  If true will start GLM model " +
            "from scratch.", level = Level.critical)
    public boolean cold_start;

    @API(help = "Handling of missing values. Either MeanImputation, Skip or PlugValues.", 
            values = { "MeanImputation", "Skip", "PlugValues" }, level = API.Level.expert, 
            direction=API.Direction.INOUT, gridable = true)
    public GLMParameters.MissingValuesHandling missing_values_handling;
    
    @API(help = "If set to dfbetas will calculate the difference in beta when a datarow is included and excluded in " +
            "the dataset.", values = { "dfbetas" }, level = API.Level.expert, gridable = false)
    public GLMParameters.Influence influence;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the" +
            " training/validation frame, use with conjunction missing_values_handling = PlugValues).", 
            direction = API.Direction.INPUT)
    public FrameKeyV3 plug_values;
    
    @API(help = "Restrict coefficients (not intercept) to be non-negative.")
    public boolean non_negative;

    @API(help = "Maximum number of iterations.  Value should >=1.  A value of 0 is only set when only the model " +
            "coefficient names and model coefficient dimensions are needed.", level = Level.secondary)
    public int max_iterations;

    @API(help = "Converge if beta changes less (using L-infinity norm) than beta esilon. ONLY applies to IRLSM solver."
            , level = Level.expert)
    public double beta_epsilon;

    @API(help = "Converge if  objective value changes less than this."+ " Default (of -1.0) indicates: If lambda_search"+
            " is set to True the value of objective_epsilon is set to .0001. If the lambda_search is set to False" +
            " and lambda is equal to zero, the value of objective_epsilon is set to .000001, for any other value" +
            " of lambda the default value of objective_epsilon is set to .0001.", level = API.Level.expert)
    public double objective_epsilon;

    @API(help = "Converge if  objective changes less (using L-infinity norm) than this, ONLY applies to L-BFGS" +
            " solver. Default (of -1.0) indicates: If lambda_search is set to False and lambda is equal to zero, the" +
            " default value of gradient_epsilon is equal to .000001, otherwise the default value is .0001. If " +
            "lambda_search is set to True, the conditional values above are 1E-8 and 1E-6 respectively.",
            level = API.Level.expert)
    public double gradient_epsilon;

    @API(help="Likelihood divider in objective value computation, default (of -1.0) will set it to 1/nobs.")
    public double obj_reg;

    @API(help = "Link function.", level = Level.secondary, values = {"family_default", "identity", "logit", "log",
            "inverse", "tweedie", "ologit"}) //"oprobit", "ologlog": will be supported.
    public GLMParameters.Link link;
    
    @API(help="Method used to estimate the dispersion parameter for Tweedie, Gamma and Negative Binomial only.",
            level = Level.secondary, values={"deviance", "pearson", "ml"})
    public GLMParameters.DispersionMethod dispersion_parameter_method;

    @API(help = "double array to initialize coefficients for GLM.  If standardize is true, the standardized " +
            "coefficients should be used.  Otherwise, use the regular coefficients.", gridable=true)
    public double[] startval;

    @API(help = "if true, will return likelihood function value.") // not gridable
    public boolean calc_like;
    
    @API(help="if true, will generate variable inflation factors for numerical predictors.  Default to false.", 
            level = Level.expert)
    public boolean generate_variable_inflation_factors;

    @API(help="Include constant term in the model", level = Level.expert)
    public boolean intercept;

    @API(help="If set, will build a model with only the intercept.  Default to false.", level = Level.expert)
    public boolean build_null_model;

    @API(help="Only used for Tweedie, Gamma and Negative Binomial GLM.  If set, will use the dispsersion parameter" +
            " in init_dispersion_parameter as the standard error and use it to calculate the p-values. Default to" +
            " false.", level=Level.expert)
    public boolean fix_dispersion_parameter;
    
    @API(help="Only used for Tweedie, Gamma and Negative Binomial GLM.  Store the initial value of dispersion " +
            "parameter.  If fix_dispersion_parameter is set, this value will be used in the calculation of p-values.",
            level=Level.expert, gridable=true)
    public double init_dispersion_parameter;
    
    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled and" +
            " the mean of response does not reflect reality.", level = Level.expert)
    public double prior;

    @API(help = "Minimum lambda used in lambda search, specified as a ratio of lambda_max (the smallest lambda that " +
            "drives all coefficients to zero).  Default indicates: if the number of observations is greater than the" +
            " number of variables, then lambda_min_ratio is set to 0.0001; if the number of observations is less than" +
            " the number of variables, then lambda_min_ratio is set to 0.01.", level = Level.expert)
    public double lambda_min_ratio;

    @API(help = "Beta constraints", direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
    public FrameKeyV3 beta_constraints;

    @API(help = "Linear constraints: used to specify linear constraints involving more than one coefficients in " +
            "standard form.  It is only supported for solver IRLSM.  It contains four columns: names (strings for " +
            "coefficient names or constant), values, types ( strings of 'Equal' or 'LessThanEqual'), constraint_numbers" +
            " (0 for first linear constraint, 1 for second linear constraint, ...).", 
            direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
    public FrameKeyV3 linear_constraints;

    @API(help="Maximum number of active predictors during computation. Use as a stopping criterion" +
    " to prevent expensive model building with many predictors." + " Default indicates: If the IRLSM solver is used," +
    " the value of max_active_predictors is set to 5000 otherwise it is set to 100000000.", direction = Direction.INPUT,
            level = Level.expert)
    public int max_active_predictors = -1;

    @API(help="A list of predictor column indices to interact. All pairwise combinations will be computed for the " +
            "list.", direction=Direction.INPUT, level=Level.expert)
    public String[] interactions;

    @API(help="A list of pairwise (first order) column interactions.", direction=Direction.INPUT, level=Level.expert)
    public StringPairV3[] interaction_pairs;

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
            " factors will be automatically computed to obtain class balance during training. Requires balance_classes.",
            level = API.Level.expert, direction = API.Direction.INOUT)
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0)." +
            " Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT)
    public float max_after_balance_size;

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.  */
    @API(help = "[Deprecated] Maximum size (# classes) for confusion matrices to be printed in the Logs.",
            level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_confusion_matrix_size;

    @API(help="Request p-values computation, p-values work only with IRLSM solver.", level = Level.secondary)
    public boolean compute_p_values;

    @API(help="If true, will fix tweedie variance power value to the value set in tweedie_variance_power.",
            level=Level.secondary, direction=Direction.INPUT)
    public boolean fix_tweedie_variance_power;

    @API(help="In case of linearly dependent columns, remove the dependent columns.", level = Level.secondary)
    public boolean remove_collinear_columns; // _remove_collinear_columns

    @API(help = "If changes in dispersion parameter estimation or loglikelihood value is smaller than " +
            "dispersion_epsilon, will break out of the dispersion parameter estimation loop using maximum " +
            "likelihood.", level = API.Level.secondary, direction = API.Direction.INOUT)
    public double dispersion_epsilon;

    @API(help = "In estimating tweedie dispersion parameter using maximum likelihood, this is used to choose the lower" +
            " and upper indices in the approximating of the infinite series summation.", 
            level = API.Level.secondary, direction = API.Direction.INOUT)
    public double tweedie_epsilon;
    
    @API(help = "Control the maximum number of iterations in the dispersion parameter estimation loop using maximum" +
            " likelihood.", level = API.Level.secondary, direction = API.Direction.INOUT)
    public int max_iterations_dispersion;

    @API(help="If set to true, will generate scoring history for GLM.  This may significantly slow down the algo.", 
            level = Level.secondary, direction = Direction.INPUT)
    public boolean generate_scoring_history;  // if enabled, will generate scoring history for iterations specified in
                                              // scoring_iteration_interval and score_every_iteration
    
    @API(help="If true, will initialize coefficients with values derived from GLM runs without linear constraints.  " +
            "Only available for linear constraints.", level = API.Level.secondary, 
            direction = API.Direction.INOUT, gridable = true)
    public boolean init_optimal_glm;

    @API(help="If true, will keep the beta constraints and linear constraints separate.  After new coefficients are " +
            "found, first beta constraints will be applied followed by the application of linear constraints.  Note " +
            "that the beta constraints in this case will not be part of the objective function.  If false, will" +
            " combine the beta and linear constraints.", level = API.Level.secondary,
            direction = API.Direction.INOUT, gridable = true)
    public boolean separate_linear_beta;

    @API(help="For constrained GLM only.  It affects the setting of eta_k+1=eta_0/power(ck+1, alpha).", 
            level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public double constraint_eta0;

    @API(help="For constrained GLM only.  It affects the setting of c_k+1=tau*c_k.", 
            level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public double constraint_tau;

    @API(help="For constrained GLM only.  It affects the setting of  eta_k = eta_0/pow(c_0, alpha).", 
            level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public double constraint_alpha;

    @API(help="For constrained GLM only.  It affects the setting of eta_k+1 = eta_k/pow(c_k, beta).", 
            level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public double constraint_beta;

    @API(help="For constrained GLM only.  It affects the initial setting of epsilon_k = 1/c_0.", 
            level = API.Level.expert, direction = API.Direction.INOUT, gridable = true)
    public double constraint_c0;
    /////////////////////
  }
}
