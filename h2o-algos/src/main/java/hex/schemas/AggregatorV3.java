package hex.schemas;

import hex.DataInfo;
import hex.aggregator.Aggregator;
import hex.aggregator.AggregatorModel;
import static hex.pca.PCAModel.*;
import water.api.API;
import water.api.ModelParametersSchema;

public class AggregatorV3 extends ModelBuilderSchema<Aggregator,AggregatorV3,AggregatorV3.AggregatorParametersV3> {

  public static final class AggregatorParametersV3 extends ModelParametersSchema<AggregatorModel.AggregatorParameters, AggregatorParametersV3> {
    static public String[] fields = new String[] {
            "model_id",
            "training_frame",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "radius_scale",
            "keep_member_indices",
            "transform",
//            "pca_method",
//            "k",
//            "max_iterations",
//            "seed",
//            "use_all_factor_levels",
//            "max_runtime_secs"
    };
    @API(help = "Radius scaling", gridable = true)
    public double radius_scale;

    @API(help = "Whether to compute and store member indices")
    public boolean keep_member_indices;

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" }, gridable = true, level= API.Level.expert)  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(help = "Method for computing PCA (Caution: Power and GLRM are currently experimental and unstable)", values = { "GramSVD", "Power", "Randomized", "GLRM" }, gridable = true, level= API.Level.expert)
    public PCAParameters.Method pca_method;

    @API(help = "Rank of matrix approximation", direction = API.Direction.INOUT, gridable = true, level= API.Level.secondary)
    public int k;

    @API(help = "Maximum number of iterations for PCA", direction = API.Direction.INOUT, gridable = true, level= API.Level.expert)
    public int max_iterations;

    @API(help = "RNG seed for initialization", direction = API.Direction.INOUT, level= API.Level.secondary)
    public long seed;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT, level= API.Level.expert)
    public boolean use_all_factor_levels;
  }
}
