package hex.schemas;

import hex.glm.GLMModel;
import hex.maxrglm.MaxRGLM;
import hex.maxrglm.MaxRGLMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.StringPairV3;

public class MaxRGLMV3 extends ModelBuilderSchema<MaxRGLM, MaxRGLMV3, MaxRGLMV3.MaxRGLMParametersV3> {
    public static final class MaxRGLMParametersV3 extends ModelParametersSchemaV3<MaxRGLMModel.MaxRGLMParameters, 
            MaxRGLMParametersV3> {
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
                "startval",  // initial starting values for fixed and randomized coefficients, double array
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
                "max_confusion_matrix_size",
                "max_runtime_secs",
                "custom_metric_func",
                "nparallelism",
                "max_predictor_number",  // denote maximum number of predictors to build models for
        };

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;

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

        @API(help = "Converge if  objective value changes less than this."+ " Default indicates: If lambda_search"+
                " is set to True the value of objective_epsilon is set to .0001. If the lambda_search is set to False" +
                " and lambda is equal to zero, the value of objective_epsilon is set to .000001, for any other value" +
                " of lambda the default value of objective_epsilon is set to .0001.", level = API.Level.expert)
        public double objective_epsilon;

        @API(help = "Converge if  objective changes less (using L-infinity norm) than this, ONLY applies to L-BFGS" +
                " solver. Default indicates: If lambda_search is set to False and lambda is equal to zero, the" +
                " default value of gradient_epsilon is equal to .000001, otherwise the default value is .0001. If " +
                "lambda_search is set to True, the conditional values above are 1E-8 and 1E-6 respectively.",
                level = API.Level.expert)
        public double gradient_epsilon;

        @API(help="Likelihood divider in objective value computation, default is 1/nobs")
        public double obj_reg;

        @API(help = "double array to initialize fixed and random coefficients for HGLM, coefficients for GLM.",
                gridable=true)
        public double[] startval;

        @API(help = "if true, will return likelihood function value for HGLM.") // not gridable
        public boolean calc_like;

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

        @API(help = "Maximum number of predictors to be considered when building GLM models.  Defaiult to 1.", 
                level = API.Level.secondary, direction = API.Direction.INPUT)
        public int max_predictor_number;

        @API(help = "number of models to build in parallel.  Default to 0.0 which is adaptive to the system capability",
                level = API.Level.secondary, gridable = true)
        public int nparallelism;
    }
}
