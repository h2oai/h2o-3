package hex.schemas;

import hex.DataInfo;
import hex.genmodel.algos.glrm.GlrmRegularizer;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.genmodel.algos.glrm.GlrmLoss;
import hex.genmodel.algos.glrm.GlrmInitialization;
import hex.svd.SVDModel.SVDParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.KeyV3;

public class GLRMV3 extends ModelBuilderSchema<GLRM, GLRMV3, GLRMV3.GLRMParametersV3> {

  public static final class GLRMParametersV3 extends ModelParametersSchemaV3<GLRMParameters, GLRMParametersV3> {
    public static String[] fields = {
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
        "loss_by_col",
        "loss_by_col_idx",
        "multi_loss",
        "period",
        "regularization_x",
        "regularization_y",
        "gamma_x",
        "gamma_y",
        "max_iterations",
        "max_updates",
        "init_step_size",
        "min_step_size",
        "seed",
        "init",
        "svd_method",
        "user_y",
        "user_x",
        "expand_user_y",
        "impute_original",
        "recover_svd",
        "max_runtime_secs",
        "export_checkpoints_dir"
    };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" }, gridable = true)  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(help = "Rank of matrix approximation", required = true, gridable = true)
    public int k;

    @API(help = "Numeric loss function", values = { "Quadratic", "Absolute", "Huber", "Poisson", "Hinge", "Logistic", "Periodic" }, gridable = true) // TODO: pull out of enum class
    public GlrmLoss loss;

    @API(help = "Categorical loss function", values = { "Categorical", "Ordinal" }, gridable = true) // TODO: pull out of categorical class
    public GlrmLoss multi_loss;

    @API(help = "Loss function by column (override)", values = { "Quadratic", "Absolute", "Huber", "Poisson", "Hinge", "Logistic", "Periodic", "Categorical", "Ordinal" }, gridable = true)
    public GlrmLoss[] loss_by_col;

    @API(help = "Loss function by column index (override)")
    public int[] loss_by_col_idx;

    @API(help = "Length of period (only used with periodic loss function)", gridable = true)
    public int period;

    @API(help = "Regularization function for X matrix", values = { "None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex" }, gridable = true) // TODO: pull out of categorical class
    public GlrmRegularizer regularization_x;

    @API(help = "Regularization function for Y matrix", values = { "None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex" }, gridable = true) // TODO: pull out of categorical class
    public GlrmRegularizer regularization_y;

    @API(help = "Regularization weight on X matrix", gridable = true)
    public double gamma_x;

    @API(help = "Regularization weight on Y matrix", gridable = true)
    public double gamma_y;

    @API(help = "Maximum number of iterations", gridable = true)
    public int max_iterations;

    @API(help = "Maximum number of updates, defaults to 2*max_iterations", gridable = true)
    public int max_updates;

    @API(help = "Initial step size", gridable = true)
    public double init_step_size;

    @API(help = "Minimum step size", gridable = true)
    public double min_step_size;

    @API(help = "RNG seed for initialization", gridable = true)
    public long seed;

    @API(help = "Initialization mode", values = { "Random", "SVD", "PlusPlus", "User" }, gridable = true) // TODO: pull out of categorical class
    public GlrmInitialization init;

    @API(help = "Method for computing SVD during initialization (Caution: Randomized is currently experimental and unstable)", values = { "GramSVD", "Power", "Randomized" }, gridable = true)   // TODO: pull out of enum class
    public SVDParameters.Method svd_method;

    @API(help = "User-specified initial Y")
    public KeyV3.FrameKeyV3 user_y;

    @API(help = "User-specified initial X")
    public KeyV3.FrameKeyV3 user_x;

    @API(help = "Frame key to save resulting X")
    public String loading_name;

    @API(help = "Expand categorical columns in user-specified initial Y")
    public boolean expand_user_y;

    @API(help = "Reconstruct original training data by reversing transform")
    public boolean impute_original;

    @API(help = "Recover singular values and eigenvectors of XY")
    public boolean recover_svd;
  }
}
