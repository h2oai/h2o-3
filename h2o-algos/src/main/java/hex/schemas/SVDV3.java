package hex.schemas;

import hex.DataInfo;
import hex.svd.SVD;
import hex.svd.SVDModel;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;

public class SVDV3 extends ModelBuilderSchema<SVD,SVDV3,SVDV3.SVDParametersV3> {

  public static final class SVDParametersV3 extends ModelParametersSchema<SVDModel.SVDParameters, SVDParametersV3> {
    static public String[] own_fields = new String[] { "transform", "nv", "max_iterations", "seed" };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of enum class
    public DataInfo.TransformType transform;

    @API(help = "Number of right singular vectors")
    public int nv;

    @API(help = "Maximum iterations")
    public int max_iterations;

    @API(help = "RNG seed for k-means++ initialization")
    public long seed;

    @API(help = "Frame key to save left singular vectors")
    public KeyV3.FrameKeyV3 ukey;
  }
}