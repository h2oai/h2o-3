package hex.schemas;

import hex.DataInfo;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel.GLRMParameters;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class GLRMV99 extends ModelBuilderSchema<GLRM,GLRMV99,GLRMV99.GLRMParametersV99> {

  public static final class GLRMParametersV99 extends ModelParametersSchema<GLRMParameters, GLRMParametersV99> {
    static public String[] fields = new String[] {
				"model_id",
				"training_frame",
				"validation_frame",
				"ignored_columns",
				"ignore_const_cols",
				"score_each_iteration",
				"loading_key",
				"transform",
				"k",
				"loss",
				"regularization_x",
				"regularization_y",
				"gamma_x",
				"gamma_y",
				"max_iterations",
				"init_step_size",
				"min_step_size",
				"seed",
				"init",
				"user_points",
				"recover_svd"
		};

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Rank of matrix approximation", required = true)
    public int k;

    @API(help = "Loss function", values = { "L2", "L1", "Huber", "Poisson", "Hinge", "Logistic" }) // TODO: pull out of enum class
    public GLRMParameters.Loss loss;

    @API(help = "Regularization function for X matrix", values = { "L2", "L1" }) // TODO: pull out of enum class
    public GLRMParameters.Regularizer regularization_x;

    @API(help = "Regularization function for Y matrix", values = { "L2", "L1" }) // TODO: pull out of enum class
    public GLRMParameters.Regularizer regularization_y;

    @API(help = "Regularization weight on X matrix")
    public double gamma_x;

    @API(help = "Regularization weight on Y matrix")
    public double gamma_y;

    @API(help = "Maximum number of iterations")
    public int max_iterations;

    @API(help = "Initial step size")
    public double init_step_size;

    @API(help = "Minimum step size")
    public double min_step_size;

    @API(help = "RNG seed for initialization")
    public long seed;

    @API(help = "Initialization mode", values = { "Random", "SVD", "PlusPlus", "User" }) // TODO: pull out of enum class
    public GLRM.Initialization init;

    @API(help = "User-specified initial Y", required = false)
    public KeyV3.FrameKeyV3 user_points;

    @API(help = "Frame key to save resulting X")
    public KeyV3.FrameKeyV3 loading_key;

    @API(help = "Recover singular values and eigenvectors of XY")
    public boolean recover_svd;
  }
}
