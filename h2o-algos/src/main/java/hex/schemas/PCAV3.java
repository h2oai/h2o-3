package hex.schemas;

import hex.DataInfo;
import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.Key;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class PCAV3 extends ModelBuilderSchema<PCA,PCAV3,PCAV3.PCAParametersV3> {

  public static final class PCAParametersV3 extends ModelParametersSchema<PCAParameters, PCAParametersV3> {
    static public String[] own_fields = new String[] { "transform", "k", "max_iterations", "seed", "loading_name", "use_all_factor_levels" };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Method for computing PCA", values = { "GramSVD", "Power", "GLRM" })   // TODO: pull out of enum class
    public PCAParameters.Method pca_method;

    @API(help = "Rank of matrix approximation", required = true, direction = API.Direction.INOUT)
    public int k;

    @API(help = "Maximum training iterations", direction = API.Direction.INOUT)
    public int max_iterations;

    @API(help = "RNG seed for initialization", direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Frame key to save resulting X", direction = API.Direction.INPUT)
    public String loading_name;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT)
    public boolean use_all_factor_levels;
  }
}
