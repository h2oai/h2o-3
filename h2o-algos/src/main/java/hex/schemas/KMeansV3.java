package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.api.API;
import water.api.schemas3.ClusteringModelParametersSchemaV3;
import water.api.schemas3.KeyV3;

public class KMeansV3 extends ClusteringModelBuilderSchema<KMeans,KMeansV3,KMeansV3.KMeansParametersV3> {

  public static final class KMeansParametersV3 extends ClusteringModelParametersSchemaV3<KMeansParameters, KMeansParametersV3> {
    static public String[] fields = new String[] {
        "model_id",
        "training_frame",
        "validation_frame",
        "nfolds",
        "keep_cross_validation_models",
        "keep_cross_validation_predictions",
        "keep_cross_validation_fold_assignment",
        "fold_assignment",
        "fold_column",
        "ignored_columns",
        "ignore_const_cols",
        "score_each_iteration",
        "k",
        "estimate_k",
        "user_points",
        "max_iterations",
        "standardize",
        "seed",
        "init",
        "max_runtime_secs",
        "categorical_encoding",
        "export_checkpoints_dir"
    };

    // Input fields
    @API(help = "This option allows you to specify a dataframe, where each row represents an initial cluster center. " +
            "The user-specified points must have the same number of columns as the training observations. " +
            "The number of rows must equal the number of clusters", required = false, level = API.Level.expert)
    public KeyV3.FrameKeyV3 user_points;

    @API(help="Maximum training iterations (if estimate_k is enabled, then this is for each inner Lloyds iteration)", gridable = true)
    public int max_iterations;        // Max iterations

    @API(help = "Standardize columns before computing distances", level = API.Level.critical, gridable = true)
    public boolean standardize = true;

    @API(help = "RNG Seed", level = API.Level.secondary /* tested, works: , dependsOn = {"k", "max_iterations"} */, gridable = true)
    public long seed;

    @API(help = "Initialization mode", values = { "Random", "PlusPlus", "Furthest", "User" }, gridable = true) // TODO: pull out of categorical class. . .
    public KMeans.Initialization init;

    @API(help = "Whether to estimate the number of clusters (<=k) iteratively and deterministically.", level = API.Level.critical, gridable = true)
    public boolean estimate_k = false;
  }
}
