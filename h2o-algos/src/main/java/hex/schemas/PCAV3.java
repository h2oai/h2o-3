package hex.schemas;

import hex.DataInfo;
import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.api.API;
import water.api.ModelParametersSchema;

public class PCAV3 extends ModelBuilderSchema<PCA,PCAV3,PCAV3.PCAParametersV3> {

  public static final class PCAParametersV3 extends ModelParametersSchema<PCAParameters, PCAParametersV3> {
    static public String[] fields = new String[] {
      "model_id",
      "training_frame",
      "validation_frame",
      "ignored_columns",
      "ignore_const_cols",
      "score_each_iteration",
      "transform",
      "pca_method",
      "k",
      "max_iterations",
      "seed",
      "use_all_factor_levels",
      "compute_metrics"
    };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" }, gridable = true)  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(help = "Method for computing PCA (Caution: Power and GLRM are currently experimental and unstable)", values = { "GramSVD", "Power", "GLRM" })   // TODO: pull out of categorical class
    public PCAParameters.Method pca_method;

    @API(help = "Rank of matrix approximation", required = true, direction = API.Direction.INOUT, gridable = true)
    public int k;

    @API(help = "Maximum training iterations", direction = API.Direction.INOUT, gridable = true)
    public int max_iterations;

    @API(help = "RNG seed for initialization", direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT)
    public boolean use_all_factor_levels;

    @API(help = "Whether to compute metrics on the training data", direction = API.Direction.INOUT)
    public boolean compute_metrics;
  }
}
