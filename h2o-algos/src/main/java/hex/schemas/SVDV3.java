package hex.schemas;

import hex.DataInfo;
import hex.svd.SVD;
import hex.svd.SVDModel;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class SVDV3 extends ModelBuilderSchema<SVD,SVDV3,SVDV3.SVDParametersV3> {

  public static final class SVDParametersV3 extends ModelParametersSchema<SVDModel.SVDParameters, SVDParametersV3> {
    static public String[] own_fields = new String[] { "transform", "nv", "max_iterations", "seed",  "keep_u", "u_name", "use_all_factor_levels" };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Number of right singular vectors")
    public int nv;

    @API(help = "Maximum iterations")
    public int max_iterations;

    @API(help = "RNG seed for k-means++ initialization")
    public long seed;

    @API(help = "Save left singular vectors?")
    public boolean keep_u;

    @API(help = "Frame key to save left singular vectors")
    public String u_name;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT)
    public boolean use_all_factor_levels;
  }
}
