package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.KeyV3.FrameKeyV3;
import water.api.SupervisedModelParametersSchema;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV3 extends SupervisedModelBuilderSchema<GLM,GLMV3,GLMV3.GLMParametersV3> {

  public static final class GLMParametersV3 extends SupervisedModelParametersSchema<GLMParameters, GLMParametersV3> {
    static public String[] own_fields = new String[]{
      "solver",
      "max_iterations",
      "beta_epsilon",
      "standardize",
      "family",
      "link",
      "tweedie_variance_power",
      "tweedie_link_power",
      "alpha",
      "lambda",
      "prior",
      "lambda_search",
      "nlambdas",
      "lambda_min_ratio",
      "use_all_factor_levels"
    };

    @API(help = "solver to use, ADMM supports more features, L_BFGS scales better for datasets with many columns", values = {"ADMM", "L_BFGS"})
    public Solver solver;

    // Input fields
    @API(help = "Standardize numeric columns to have zero mean and unit variance")
    public boolean standardize;

    @API(help = "Maximum number of iterations")
    public int max_iterations = 50;

    @API(help = "beta esilon -> consider being converged if L1 norm of the current beta change is below this threshold")
    public double beta_epsilon;

    @API(help = "Family.", values = {"gaussian", "binomial", "poisson", "gamma" /* , "tweedie" */})
    // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "", level = Level.secondary, values = {"family_default", "identity", "logit", "log", "inverse", "tweedie"})
    public GLMParameters.Link link = Link.family_default;

    @API(help = "Tweedie variance power", level = Level.secondary)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level = Level.secondary)
    public double tweedie_link_power;

    @API(help = "distribution of regularization between L1 and L2.", level = Level.secondary)
    public double[] alpha;

    @API(help = "regularization strength", level = Level.secondary, required = false)
    public double[] lambda = null;

    @API(help = "prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.", level = Level.expert)
    public double prior;

    @API(help = "use lambda search starting at lambda max, given lambda is then interpreted as lambda min", level = Level.secondary)
    public boolean lambda_search;

    @API(help = "number of lambdas to be used in a search", level = Level.expert)
    public int nlambdas;

    @API(help = "min lambda used in lambda search, specified as a ratio of lambda_max", level = Level.expert)
    public double lambda_min_ratio;

    @API(help = "By default, first factor level is skipped from the possible set of predictors. Set this flag if you want use all of the levels. Needs sufficient regularization to solve!", level = Level.secondary)
    public boolean use_all_factor_levels;

    @API(help = "beta constraints", direction = API.Direction.INPUT /* Not required, to allow initial params validation: , required=true */)
    public FrameKeyV3 beta_constraints;

    @API(help="Maximum number of active predictors during computation. Use as a stopping criterium to prevent expensive model building with many predictors.", direction = Direction.INPUT)
    public int max_active_predictors = -1;
  }
}
