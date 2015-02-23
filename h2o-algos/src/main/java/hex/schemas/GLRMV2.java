package hex.schemas;

import hex.DataInfo;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel.GLRMParameters;
import water.Key;
import water.api.API;
import water.api.KeyV1;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class GLRMV2 extends ModelBuilderSchema<GLRM,GLRMV2,GLRMV2.GLRMParametersV2> {

  public static final class GLRMParametersV2 extends ModelParametersSchema<GLRMParameters, GLRMParametersV2> {
    static public String[] own_fields = new String[] { "transform", "k", "gamma", "max_iterations", "seed", "init", "user_points" };

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
    public GLRM.Initialization init;

    @API(help = "User-specified initial Y", required = false)
    public KeyV1.FrameKeyV1 user_points;

    @API(help = "Frame key to save resulting X")
    public KeyV1.FrameKeyV1 loading_key;
  }
}
