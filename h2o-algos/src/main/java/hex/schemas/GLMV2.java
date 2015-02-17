package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.DKV;
import water.api.API;
import water.api.API.Level;
import water.api.SupervisedModelParametersSchema;
import water.fvec.Frame;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV2 extends SupervisedModelBuilderSchema<GLM,GLMV2,GLMV2.GLMParametersV2> {

  public static final class GLMParametersV2 extends SupervisedModelParametersSchema<GLMParameters, GLMParametersV2> {
    static public String[] own_fields = new String[] {
      "solver",
      "max_iter",
      "beta_eps",
      "standardize",
      "family",
      "link",
      "tweedie_variance_power",
      "tweedie_link_power",
      "alpha",
      "lambda",
      "prior1",
      "lambda_search",
      "nlambdas",
      "lambda_min_ratio",
      "use_all_factor_levels",
      "n_folds"
    };

    @API(help="solver to use, ADMM supports more features, L_BFGS scales better for datasets with many columns", values = {"ADMM", "L_BFGS"})
    public Solver solver;

    // Input fields
    @API(help = "Standardize numeric columns to have zero mean and unit variance.")
    public boolean standardize;

    @API(help = "Maximum number of iterations. ")
    public int max_iter = 50;

    @API(help="beta esilon -> consider being converged if L1 norm of the current beta change is below this threshold")
    public double beta_eps;

    @API(help = "Family.", values={ "gaussian", "binomial", "poisson", "gamma" /* , "tweedie" */}) // took tweedie out since it's not reliable
    public GLMParameters.Family family;

    @API(help = "", level= Level.secondary, values={ "family_default", "identity", "logit", "log", "inverse", "tweedie" })
    public GLMParameters.Link link = Link.family_default;

    @API(help = "Tweedie variance power", level=Level.secondary)
    public double tweedie_variance_power;

    @API(help = "Tweedie link power", level=Level.secondary)
    public double tweedie_link_power;

    @API(help = "distribution of regularization between L1 and L2.", level=Level.secondary)
    public double [] alpha;

    @API(help = "regularization strength", level=Level.secondary)
    public double [] lambda;

    @API(help="prior probability for y==1. To be used only for logistic regression iff the data has been sampled and the mean of response does not reflect reality.",level=Level.expert)
    public double prior1;

    @API(help="use lambda search starting at lambda max, given lambda is then interpreted as lambda min",level=Level.secondary)
    public boolean lambda_search;

    @API(help="number of lambdas to be used in a search",level=Level.expert)
    public int nlambdas;

    @API(help="min lambda used in lambda search, specified as a ratio of lambda_max",level=Level.expert)
    public double lambda_min_ratio;

    @API(help="By default, first factor level is skipped from the possible set of predictors. Set this flag if you want use all of the levels. Needs sufficient regularization to solve!",level=Level.secondary)
    public boolean use_all_factor_levels;

    @API(help = "validation folds")
    public int n_folds;

    @Override
    public GLMParametersV2 fillFromImpl(GLMParameters impl) {
      super.fillFromImpl(impl);
      this.do_classification = family == Family.binomial;
      return this;
    }

    @Override
    public GLMParameters fillImpl(GLMParameters impl) {
      super.fillImpl(impl);
      impl._convert_to_enum = false;
      return impl;
    }


  }

  //==========================
  // Custom adapters go here

  // Return a URL to invoke GLM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GLM?training_frame="+fr._key; }
}
