package hex.schemas;

import hex.glm.GLMModel;
import hex.modelselection.ModelSelection;
import hex.modelselection.ModelSelectionModel;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class ModelSelectionV3 extends ModelBuilderSchema<ModelSelection, ModelSelectionV3, ModelSelectionV3.ModelSelectionParametersV3> {
    public static final class ModelSelectionParametersV3 extends ModelParametersSchemaV3<ModelSelectionModel.ModelSelectionParameters,
            ModelSelectionParametersV3> {
        public static final String[] fields = new String[]{
                "model_id",
                "training_frame",
                "validation_frame",
                "nfolds",
                "seed",
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
                "link",
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
                "intercept",
                "non_negative",
                "max_iterations",
                "objective_epsilon",
                "beta_epsilon",
                "gradient_epsilon",
                "startval",  // initial starting values for coefficients, double array
                "prior",
                "cold_start", // if true, will start GLM model from initial values and conditions
                "lambda_min_ratio",
                "beta_constraints",
                "max_active_predictors",
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
                "nparallelism",
                "max_predictor_number",  // denote maximum number of predictors to build models for
                "min_predictor_number",
                "mode", // naive, maxr, maxrsweep, backward
                "build_glm_model",
                "p_values_threshold",
                "influence",
                "multinode_mode"
        };

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;

        // Input fields
        @API(help = "Family. For maxr/maxrsweep, only gaussian.  For backward, ordinal and multinomial families are not supported",
                values = {"AUTO", "gaussian", "binomial", "fractionalbinomial", "quasibinomial", "poisson",
                        "gamma", "tweedie", "negativebinomial"}, level = API.Level.critical)
        // took tweedie out since it's not reliable
        public GLMModel.GLMParameters.Family family;

        @API(help = "Tweedie variance power", level = API.Level.critical, gridable = true)
        public double tweedie_variance_power;

        @API(help = "Tweedie link power", level = API.Level.critical, gridable = true)
        public double tweedie_link_power;

        @API(help = "Theta", level = API.Level.critical, gridable = true)
        public double theta; // used by negtaive binomial distribution family


        @API(help = "AUTO will set the solver based on given data and the other parameters. IRLSM is fast on on " +
                "problems with small number of predictors and for lambda-search with L1 penalty, L_BFGS scales " +
                "better for datasets with many columns.", values = {"AUTO", "IRLSM", "L_BFGS",
                "COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT", "GRADIENT_DESCENT_LH", "GRADIENT_DESCENT_SQERR"},
                level = API.Level.critical)
        public GLMModel.GLMParameters.Solver solver;

        @API(help = "Distribution of regularization between the L1 (Lasso) and L2 (Ridge) penalties. A value of 1 for" +
                " alpha represents Lasso regression, a value of 0 produces Ridge regression, and anything in between" +
                " specifies the amount of mixing between the two. Default value of alpha is 0 when SOLVER = 'L-BFGS';" +
                " 0.5 otherwise.", level = API.Level.critical, gridable = true)
        public double[] alpha;

        @API(help = "Regularization strength", required = false, level = API.Level.critical, gridable = true)
        public double[] lambda;

        @API(help = "Use lambda search starting at lambda max, given lambda is then interpreted as lambda min", 
                level = API.Level.critical)
        public boolean lambda_search;

        @API(help = "For maxrsweep only.  If enabled, will attempt to perform sweeping action using multiple nodes in " +
                "the cluster.  Defaults to false.",
                level = API.Level.critical)
        public boolean multinode_mode;

        @API(help = "For maxrsweep mode only.  If true, will return full blown GLM models with the desired predictor" +
                "subsets.  If false, only the predictor subsets, predictor coefficients are returned.  This is for" +
                "speeding up the model selection process.  The users can choose to build the GLM models themselves" +
                "by using the predictor subsets themselves.  Defaults to false.",
                level = API.Level.critical)
        public boolean build_glm_model;

        @API(help="Stop early when there is no more relative improvement on train or validation (if provided)")
        public boolean early_stopping;

        @API(help = "Number of lambdas to be used in a search." +
                " Default indicates: If alpha is zero, with lambda search" +
                " set to True, the value of nlamdas is set to 30 (fewer lambdas" +
                " are needed for ridge regression) otherwise it is set to 100.", level = API.Level.critical)
        public int nlambdas;

        @API(help = "Perform scoring for every score_iteration_interval iterations", level = API.Level.secondary)
        public int score_iteration_interval;

        @API(help = "Standardize numeric columns to have zero mean and unit variance", level = API.Level.critical)
        public boolean standardize;

        @API(help = "Only applicable to multiple alpha/lambda values.  If false, build the next model for next set" +
                " of alpha/lambda values starting from the values provided by current model.  If true will start GLM" +
                " model from scratch.", level = API.Level.critical)
        public boolean cold_start;

        @API(help = "Handling of missing values. Either MeanImputation, Skip or PlugValues.", 
                values = { "MeanImputation", "Skip", "PlugValues" }, level = API.Level.expert, 
                direction=API.Direction.INOUT, gridable = true)
        public GLMModel.GLMParameters.MissingValuesHandling missing_values_handling;

        @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of" +
                " the training/validation frame, use with conjunction missing_values_handling = PlugValues)",
                direction = API.Direction.INPUT)
        public KeyV3.FrameKeyV3 plug_values;

        @API(help = "Restrict coefficients (not intercept) to be non-negative")
        public boolean non_negative;

        @API(help = "Maximum number of iterations", level = API.Level.secondary)
        public int max_iterations;

        @API(help = "Converge if  beta changes less (using L-infinity norm) than beta esilon, ONLY applies to IRLSM" +
                " solver ", level = API.Level.expert)
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

        @API(help="Likelihood divider in objective value computation, default (of -1.0) will set it to 1/nobs")
        public double obj_reg;

        @API(help = "Link function.", level = API.Level.secondary, values = {"family_default", "identity", "logit", "log",
                "inverse", "tweedie", "ologit"}) //"oprobit", "ologlog": will be supported.
        public GLMModel.GLMParameters.Link link;

        @API(help = "Double array to initialize coefficients for GLM.",
                gridable=true)
        public double[] startval;

        @API(help = "If true, will return likelihood function value for GLM.") // not gridable
        public boolean calc_like;
        
        @API(level = API.Level.critical, direction = API.Direction.INOUT,
                valuesProvider = ModelSelectionModeProvider.class,
                help = "Mode: Used to choose model selection algorithms to use.  Options include "
                        + "'allsubsets' for all subsets, "
                        + "'maxr' that uses sequential replacement and GLM to build all models, slow but works with cross-validation, validation frames for more robust results, "
                        + "'maxrsweep' that uses sequential replacement and sweeping action, much faster than 'maxr', "
                        + "'backward' for backward selection."
        )
        public ModelSelectionModel.ModelSelectionParameters.Mode mode;

        @API(help="Include constant term in the model", level = API.Level.expert)
        public boolean intercept;

        @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been " +
                "sampled and the mean of response does not reflect reality.", level = API.Level.expert)
        public double prior;

        @API(help = "Minimum lambda used in lambda search, specified as a ratio of lambda_max (the smallest lambda" +
                " that drives all coefficients to zero). Default indicates: if the number of observations is greater" +
                " than the number of variables, then lambda_min_ratio is set to 0.0001; if the number of observations" +
                " is less than the number of variables, then lambda_min_ratio is set to 0.01.", 
                level = API.Level.expert)
        public double lambda_min_ratio;

        @API(help = "Beta constraints", direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
        public KeyV3.FrameKeyV3 beta_constraints;

        @API(help="Maximum number of active predictors during computation. Use as a stopping criterion to prevent" +
                " expensive model building with many predictors." + " Default indicates: If the IRLSM solver is used," +
                " the value of max_active_predictors is set to 5000 otherwise it is set to 100000000.",
                direction = API.Direction.INPUT, level = API.Level.expert)
        public int max_active_predictors = -1;

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
        @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, " +
                "sampling factors will be automatically computed to obtain class balance during training. Requires" +
                " balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
        public float[] class_sampling_factors;

        /**
         * When classes are balanced, limit the resulting dataset size to the
         * specified multiple of the original dataset size.
         */
        @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0)." +
                " Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, 
                direction = API.Direction.INOUT)
        public float max_after_balance_size;

        /** For classification models, the maximum size (in terms of classes) of
         *  the confusion matrix for it to be printed. This option is meant to
         *  avoid printing extremely large confusion matrices.  */
        @API(help = "[Deprecated] Maximum size (# classes) for confusion matrices to be printed in the Logs", 
                level = API.Level.secondary, direction = API.Direction.INOUT)
        public int max_confusion_matrix_size;

        @API(help="Request p-values computation, p-values work only with IRLSM solver and no regularization", 
                level = API.Level.secondary, direction = API.Direction.INPUT)
        public boolean compute_p_values; // _remove_collinear_columns

        @API(help="In case of linearly dependent columns, remove some of the dependent columns", 
                level = API.Level.secondary, direction = API.Direction.INPUT)
        public boolean remove_collinear_columns; // _remove_collinear_columns

        @API(help = "Maximum number of predictors to be considered when building GLM models.  Defaults to 1.", 
                level = API.Level.secondary, direction = API.Direction.INPUT)
        public int max_predictor_number;

        @API(help = "For mode = 'backward' only.  Minimum number of predictors to be considered when building GLM " +
                "models starting with all predictors to be included.  Defaults to 1.",
                level = API.Level.secondary, direction = API.Direction.INPUT)
        public int min_predictor_number;

        @API(help = "number of models to build in parallel.  Defaults to 0.0 which is adaptive to the system capability",
                level = API.Level.secondary, gridable = true)
        public int nparallelism;

        @API(help = "For mode='backward' only.  If specified, will stop the model building process when all coefficients" +
                "p-values drop below this threshold ", level = API.Level.expert)
        public double p_values_threshold;

        @API(help = "If set to dfbetas will calculate the difference in beta when a datarow is included and excluded in " +
                "the dataset.", values = { "dfbetas" }, level = API.Level.expert, gridable = false)
        public GLMModel.GLMParameters.Influence influence;
    }

    public static final class ModelSelectionModeProvider extends EnumValuesProvider<ModelSelectionModel.ModelSelectionParameters.Mode> {
        public ModelSelectionModeProvider() { super(ModelSelectionModel.ModelSelectionParameters.Mode.class); }
    }
}
