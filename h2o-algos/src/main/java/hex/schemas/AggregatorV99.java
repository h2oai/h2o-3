package hex.schemas;

import hex.DataInfo;
import hex.aggregator.Aggregator;
import hex.aggregator.AggregatorModel;
import static hex.pca.PCAModel.*;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class AggregatorV99 extends ModelBuilderSchema<Aggregator,AggregatorV99,AggregatorV99.AggregatorParametersV99> {

  public static final class AggregatorParametersV99 extends ModelParametersSchemaV3<AggregatorModel.AggregatorParameters, AggregatorParametersV99> {
    static public String[] fields = new String[] {
            "model_id",
            "training_frame",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "radius_scale",
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
