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
				"loading_name",
				"transform",
				"k",
				"loss",
                "multi_loss",
                "period",
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

    @API(help = "Rank of matrix approximation", required = true, gridable = true)
    public int k;

    @API(help = "Numeric loss function", values = { "Quadratic", "L1", "Huber", "Poisson", "Hinge", "Logistic", "Periodic" }) // TODO: pull out of enum class
    public GLRMParameters.Loss loss;

    @API(help = "Enum loss function", values = { "Categorical", "Ordinal" }) // TODO: pull out of enum class
    public GLRMParameters.Loss multi_loss;

    @API(help = "Loss function by column (override)", values = { "Quadratic", "L1", "Huber", "Poisson", "Hinge", "Logistic", "Periodic", "Categorical", "Ordinal" })
    public GLRMParameters.Loss[] loss_by_col;

    @API(help = "Loss function by column index (override)")
    public int[] loss_by_col_idx;

    @API(help = "Length of period (only used with periodic loss function)", gridable = true)
    public int period;

    @API(help = "Regularization function for X matrix", values = { "None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex" }) // TODO: pull out of enum class
    public GLRMParameters.Regularizer regularization_x;

    @API(help = "Regularization function for Y matrix", values = { "None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex" }) // TODO: pull out of enum class
    public GLRMParameters.Regularizer regularization_y;

    @API(help = "Regularization weight on X matrix", gridable = true)
    public double gamma_x;

    @API(help = "Regularization weight on Y matrix", gridable = true)
    public double gamma_y;

    @API(help = "Maximum number of iterations")
    public int max_iterations;

    @API(help = "Initial step size", gridable = true)
    public double init_step_size;

    @API(help = "Minimum step size", gridable = true)
    public double min_step_size;

    @API(help = "RNG seed for initialization")
    public long seed;

    @API(help = "Initialization mode", values = { "Random", "SVD", "PlusPlus", "User" }) // TODO: pull out of enum class
    public GLRM.Initialization init;

    @API(help = "User-specified initial Y", required = false)
    public KeyV3.FrameKeyV3 user_points;

    @API(help = "Frame key to save resulting X")
    public String loading_name;
    // public KeyV3.FrameKeyV3 loading_key;

    @API(help = "Recover singular values and eigenvectors of XY")
    public boolean recover_svd;
  }
}
