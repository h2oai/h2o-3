package hex.schemas;

import hex.DataInfo;
import hex.aggregator.Aggregator;
import hex.aggregator.AggregatorModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

import static hex.pca.PCAModel.PCAParameters;

public class AggregatorV99 extends ModelBuilderSchema<Aggregator,AggregatorV99,AggregatorV99.AggregatorParametersV99> {

  public static final class AggregatorParametersV99 extends ModelParametersSchemaV3<AggregatorModel.AggregatorParameters, AggregatorParametersV99> {
    static public String[] fields = new String[] {
            "model_id",
            "training_frame",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "target_num_exemplars",
            "rel_tol_num_exemplars",
//            "radius_scale",
            "transform",
            "categorical_encoding",
            "save_mapping_frame",
            "num_iteration_without_new_exemplar",
//            "pca_method",
//            "k",
//            "max_iterations",
//            "seed",
//            "use_all_factor_levels",
//            "max_runtime_secs"
            "export_checkpoints_dir"
    };
//    @API(help = "Radius scaling", gridable = true)
//    public double radius_scale;

    @API(help = "Transformation of training data", values = { "NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE" }, gridable = true, level= API.Level.expert)  // TODO: pull out of categorical class
    public DataInfo.TransformType transform;

    @API(help = "Method for computing PCA (Caution: GLRM is currently experimental and unstable)", values = { "GramSVD", "Power", "Randomized", "GLRM" }, gridable = true, level= API.Level.expert)
    public PCAParameters.Method pca_method;

    @API(help = "Rank of matrix approximation", direction = API.Direction.INOUT, gridable = true, level= API.Level.secondary)
    public int k;

    @API(help = "Maximum number of iterations for PCA", direction = API.Direction.INOUT, gridable = true, level= API.Level.expert)
    public int max_iterations;

    @API(help = "Targeted number of exemplars", direction = API.Direction.INOUT, gridable = true, level= API.Level.secondary)
    public int target_num_exemplars;

    @API(help = "Relative tolerance for number of exemplars (e.g, 0.5 is +/- 50 percents)", direction = API.Direction.INOUT, gridable = true, level= API.Level.secondary)
    public double rel_tol_num_exemplars;

    @API(help = "RNG seed for initialization", direction = API.Direction.INOUT, level= API.Level.secondary)
    public long seed;

    @API(help = "Whether first factor level is included in each categorical expansion", direction = API.Direction.INOUT, level= API.Level.expert)
    public boolean use_all_factor_levels;

    @API(help = "Whether to export the mapping of the aggregated frame", direction = API.Direction.INOUT, level= API.Level.expert)
    public boolean save_mapping_frame;

    @API(help = "The number of iterations to run before aggregator exits if the number of exemplars collected didn't change", direction = API.Direction.INOUT, level= API.Level.expert)
    public int num_iteration_without_new_exemplar;
  }
}
