package hex.schemas;

import hex.DataInfo;
import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class PCAV3 extends ModelBuilderSchema<PCA,PCAV3,PCAV3.PCAParametersV3> {

  public static final class PCAParametersV3 extends ModelParametersSchema<PCAParameters, PCAParametersV3> {
    static public String[] own_fields = new String[] { "transform", "k", "gamma", "max_iterations", "seed", "init", "user_points", "loading_key" };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Rank of matrix approximation", required = true)
    public int k;

    @API(help = "Regularization weight")
    public double gamma;

    @API(help = "Maximum training iterations")
    public int max_iterations;

    @API(help = "RNG seed for k-means++ initialization")
    public long seed;

    @API(help = "Initialization mode", values = { "PlusPlus", "User" }) // TODO: pull out of enum class
    public PCA.Initialization init;

    @API(help = "User-specified initial Y", required = false)
    public KeyV3.FrameKeyV3 user_points;

    @API(help = "Frame key to save resulting X")
    public KeyV3.FrameKeyV3 loading_key;
  }
}
