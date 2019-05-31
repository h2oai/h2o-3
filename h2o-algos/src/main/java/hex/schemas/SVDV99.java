package hex.schemas;

import hex.DataInfo;
import hex.svd.SVD;
import hex.svd.SVDModel.SVDParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class SVDV99 extends ModelBuilderSchema<SVD,SVDV99,SVDV99.SVDParametersV99> {

  public static final class SVDParametersV99 extends ModelParametersSchemaV3<SVDParameters, SVDParametersV99> {
    static public String[] fields = new String[] {
        "model_id",
        "training_frame",
        "validation_frame",
        "ignored_columns",
        "ignore_const_cols",
        "score_each_iteration",
        "transform",
                "svd_method",
        "nv",
        "max_iterations",
        "seed",
        "keep_u",
        "u_name",
        "use_all_factor_levels",
        "max_runtime_secs",
        "export_checkpoints_dir"
    };

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" })  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(help = "Method for computing SVD (Caution: Randomized is currently experimental and unstable)", values = { "GramSVD", "Power", "Randomized" })   // TODO: pull out of enum class
    public SVDParameters.Method svd_method;

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
