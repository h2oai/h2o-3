package hex.schemas;

import hex.glm.GLMModel.GLMParameters;
import hex.hglm.HGLM;
import hex.hglm.HGLMModel;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class HGLMV3 extends ModelBuilderSchema<HGLM, HGLMV3, HGLMV3.HGLMParametersV3> {

  public static final class HGLMParametersV3 extends ModelParametersSchemaV3<HGLMModel.HGLMParameters, HGLMParametersV3> {
    public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "validation_frame",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "offset_column",
            "weights_column",
            "max_runtime_secs",
            "custom_metric_func",
            "score_each_iteration",
            "score_iteration_interval",
            "seed",
            "missing_values_handling",
            "plug_values",
            "family",
            "rand_family",
            "max_iterations",
            "initial_fixed_effects",
            "initial_random_effects",
            "initial_t_matrix",
            "tau_u_var_init",
            "tau_e_var_init",
            "random_columns",
            "method",
            "em_epsilon",
            "random_intercept", 
            "group_column",
            "gen_syn_data"
    };

    @API(help = "Perform scoring for every score_iteration_interval iterations.", level = Level.secondary)
    public int score_iteration_interval;

    @API(help = "Seed for pseudo random number generator (if applicable).", gridable = true)
    public long seed;

    @API(help = "Handling of missing values. Either MeanImputation, Skip or PlugValues.",
            values = { "MeanImputation", "Skip", "PlugValues"}, level = API.Level.expert,
            direction=API.Direction.INOUT, gridable = true)
    public GLMParameters.MissingValuesHandling missing_values_handling;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the" +
            " training/validation frame, use with conjunction missing_values_handling = PlugValues).",
            direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 plug_values;
    
    // Input fields
    @API(help = "Family. Only gaussian is supported now.",
            values = {"gaussian"}, level = Level.critical)
    public GLMParameters.Family family;
    
    @API(help = "Set distribution of random effects.  Only Gaussian is implemented now.",
            values = {"gaussian"}, level = Level.critical)
    public GLMParameters.Family rand_family;

    @API(help = "Maximum number of iterations.  Value should >=1.  A value of 0 is only set when only the model " +
            "coefficient names and model coefficient dimensions are needed.", level = Level.secondary)
    public int max_iterations;
    
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable=true,
            help = "An array that contains initial values of the fixed effects coefficient.")
    public double[] initial_fixed_effects;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable=true,
            help = "A H2OFrame id that contains initial values of the random effects coefficient.  The row names should" +
                    "be the random coefficient names.  If you are not sure what the random coefficient names are," +
                    " build HGLM model with max_iterations = 0 and checkout the model output field " +
                    "random_coefficient_names.  The number of rows of this frame should be the number of level 2" +
                    " units.  Again, to figure this out, build HGLM model with max_iterations=0 and check out " +
                    "the model output field group_column_names.  The number of rows should equal the length of the" +
                    "group_column_names.")
    public KeyV3.FrameKeyV3 initial_random_effects;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable=true,
            help = "A H2OFrame id that contains initial values of the T matrix.  It should be a positive symmetric matrix.")
    public KeyV3.FrameKeyV3 initial_t_matrix;

    @API(help = "Initial variance of random coefficient effects.  If set, should provide a value > 0.0.  If not set, " +
            "will be randomly set in the model building process."
            , level = Level.expert, gridable = true)
    public double tau_u_var_init;

    @API(help = "Initial variance of random noise.  If set, should provide a value > 0.0.  If not set, will be randomly" +
            " set in the model building process."
            , level = Level.expert, gridable = true)
    public double tau_e_var_init;

    @API(help = "Random columns indices for HGLM.", gridable=true)
    public String[] random_columns;

    @API(help = "We only implemented EM as a method to obtain the fixed, random coefficients and the various variances.", 
            values = {"EM"}, level = Level.critical)
    public HGLMModel.HGLMParameters.Method method;

    @API(help = "Converge if beta/ubeta/tmat/tauEVar changes less (using L-infinity norm) than em esilon. ONLY applies to EM method."
            , level = Level.expert)
    public double em_epsilon;

    @API(help="If true, will allow random component to the GLM coefficients.", direction=Direction.INPUT, gridable=true)
    public boolean random_intercept;
    
    @API(help="Group column is the column that is categorical and used to generate the groups in HGLM", gridable=true)
    public String group_column;

    @API(help="If true, add gaussian noise with variance specified in parms._tau_e_var_init.", 
            direction=Direction.INPUT, gridable=true)
    public boolean gen_syn_data;
  }
}
