package hex.schemas;

import hex.gam.GAM;
import hex.gam.GAMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.StringPairV3;

public class GAMV3 extends ModelBuilderSchema<GAM, GAMV3, GAMV3.GAMParametersV3> {
  public static final class GAMParametersV3 extends ModelParametersSchemaV3<GAMModel.GAMParameters, GAMParametersV3> {
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
            "remove_collinear_columns",
            "splines_non_negative",
            "intercept",
            "non_negative",
            "max_iterations",
            "objective_epsilon",
            "beta_epsilon",
            "gradient_epsilon",
            "link",
            "startval",  // initial starting values for fixed and randomized coefficients, double array
            "prior",
            "cold_start", // if true, will start GLM model from initial values and conditions
            "lambda_min_ratio",
            "beta_constraints",
            "max_active_predictors",
            "interactions",
            "interaction_pairs",
            "obj_reg",
            "export_checkpoints_dir",
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
            "num_knots",  // array: number of knots for each predictor
            "spline_orders",  // order of I-splines
            "knot_ids", // string array storing frame keys that contains knot location
            "gam_columns",  // array: predictor column names array
            "standardize_tp_gam_cols", // standardize TP gam columns before transformation
            "scale_tp_penalty_mat", // scale penalty matrix
            "bs", // array, name of basis functions used
            "scale", // array, smoothing parameter for GAM,
            "keep_gam_cols",
            "store_knot_locations",
            "auc_type"
    };

    @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
    public long seed;

    // Input fields
    @API(help = "Family. Use binomial for classification with logistic regression, others are for regression problems.", values = {"AUTO", "gaussian", "binomial","quasibinomial","ordinal", "multinomial", "poisson", "gamma", "tweedie", "negativebinomial", "fractionalbinomial"}, level = Level.critical)
    // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "Tweedie variance power", level = Level.critical, gridable = true)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level = Level.critical, gridable = true)
    public double tweedie_link_power;

    @API(help = "Theta", level = Level.critical, gridable = true)
    public double theta; // used by negtaive binomial distribution family

    @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on problems with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales better for datasets with many columns.", values = {"AUTO", "IRLSM", "L_BFGS","COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT", "GRADIENT_DESCENT_LH", "GRADIENT_DESCENT_SQERR"}, level = Level.critical)
    public Solver solver;

    @API(help = "Distribution of regularization between the L1 (Lasso) and L2 (Ridge) penalties. A value of 1 for alpha represents Lasso regression, a value of 0 produces Ridge regression, and anything in between specifies the amount of mixing between the two. Default value of alpha is 0 when SOLVER = 'L-BFGS'; 0.5 otherwise.", level = Level.critical, gridable = true)
    public double[] alpha;

    @API(help = "Regularization strength", level = Level.critical, gridable = true)
    public double[] lambda;

    @API(help = "double array to initialize coefficients for GAM.", gridable=true)
    public double[] startval;

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

    @API(help = "Handling of missing values. Either MeanImputation, Skip or PlugValues.", values = { "MeanImputation", "Skip", "PlugValues" }, level = API.Level.expert, direction=API.Direction.INOUT, gridable = true)
    public GLMParameters.MissingValuesHandling missing_values_handling;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the training/validation frame, use with conjunction missing_values_handling = PlugValues)", direction = API.Direction.INPUT)
    public FrameKeyV3 plug_values;

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

    @API(help = "Link function.", level = Level.secondary, values = {"family_default", "identity", "logit", "log",
            "inverse", "tweedie", "ologit"}) //"oprobit", "ologlog": will be supported.
    public GLMParameters.Link link;

    @API(help="Include constant term in the model", level = Level.expert)
    public boolean intercept;

    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.", level = Level.expert)
    public double prior;

    @API(help = "Only applicable to multiple alpha/lambda values when calling GLM from GAM.  If false, build the next" +
            " model for next set of alpha/lambda values starting from the values provided by current model.  If true" +
            " will start GLM model from scratch.", level = Level.critical)
    public boolean cold_start;

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

    @API(help="Request p-values computation, p-values work only with IRLSM solver and no regularization", level = Level.secondary, direction = Direction.INPUT)
    public boolean compute_p_values; // _remove_collinear_columns

    @API(help="In case of linearly dependent columns, remove some of the dependent columns", level = Level.secondary, direction = Direction.INPUT)
    public boolean remove_collinear_columns; // _remove_collinear_columns

    @API(help="If set to true, will return knot locations as double[][] array for gam column names found knots_for_gam." +
            "  Default to false.", level = Level.secondary, direction = Direction.INPUT)    
    public boolean store_knot_locations;

    @API(help = "Number of knots for gam predictors.  If specified, must specify one for each gam predictor.  For " +
            "monotone I-splines, mininum = 2, for cs spline, minimum = 3.  For thin plate, minimum is size of " +
            "polynomial basis + 2.", 
            level = Level.critical, gridable = true)
    public int[] num_knots;
    
    @API(help = "Order of I-splines or NBSplineTypeI M-splines used for gam predictors. If specified, must be the " +
            "same size as gam_columns.  For I-splines, the spline_orders will be the same as the polynomials used to " +
            "generate the splines.  For M-splines, the polynomials used to generate the splines will be " +
            "spline_order-1.  Values for bs=0 or 1 will be ignored.", level = Level.critical, gridable = true)
    public int[] spline_orders;

    @API(help = "Valid for I-spline (bs=2) only.  True if the I-splines are monotonically increasing (and monotonically " +
            "non-decreasing) and False if the I-splines are monotonically decreasing (and monotonically non-increasing)." +
            "  If specified, must be the same size as gam_columns.  Values for other spline types " +
            "will be ignored.  Default to true.", level = Level.critical, gridable = true)
    public boolean[] splines_non_negative;

    @API(help = "Arrays of predictor column names for gam for smoothers using single or multiple predictors like " +
            "{{'c1'},{'c2','c3'},{'c4'},...}", required = true, level = Level.critical, gridable = true)
    public String[][] gam_columns;

    @API(help = "Smoothing parameter for gam predictors.  If specified, must be of the same length as gam_columns",
            level = Level.critical, gridable = true)
    public double[] scale;

    @API(help = "Basis function type for each gam predictors, 0 for cr, 1 for thin plate regression with knots, 2 for" +
            " monotone I-splines, 3 for NBSplineTypeI M-splines (refer to doc " +
            "here: https://github.com/h2oai/h2o-3/issues/6926).  If specified, must be the same size as " +
            "gam_columns", level = Level.critical, gridable = true)
    public int[] bs;

    @API(help="Save keys of model matrix", level = Level.secondary, direction = Direction.INPUT)
    public boolean keep_gam_cols; // if true will save keys storing GAM columns

    @API(help="standardize tp (thin plate) predictor columns", level = Level.secondary, direction = Direction.INPUT)
    public boolean standardize_tp_gam_cols; // if true, will standardize predictor columns before gamification

    @API(help="Scale penalty matrix for tp (thin plate) smoothers as in R", level = Level.secondary, direction = Direction.INPUT)
    public boolean scale_tp_penalty_mat; // if true, will apply scaling to the penalty matrix CS
    
    @API(help="Array storing frame keys of knots.  One for each gam column set specified in gam_columns", 
            level = Level.secondary, direction = Direction.INPUT)
    public String[] knot_ids;
  }
}
