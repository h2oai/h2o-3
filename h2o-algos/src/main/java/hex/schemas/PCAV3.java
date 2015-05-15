package hex.schemas;

import hex.DataInfo;
import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class PCAV3 extends ModelBuilderSchema<PCA,PCAV3,PCAV3.PCAParametersV3> {

  public static final class PCAParametersV3 extends ModelParametersSchema<PCAParameters, PCAParametersV3> {
    static public String[] own_fields = new String[] { "transform", "k", "max_iterations", "seed", "loading_key" };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Rank of matrix approximation", required = true)
    public int k;

    @API(help = "Maximum training iterations")
    public int max_iterations;

    @API(help = "RNG seed for initialization")
    public long seed;

    @API(help = "Frame key to save resulting X")
    public KeyV3.FrameKeyV3 loading_key;
  }
}
