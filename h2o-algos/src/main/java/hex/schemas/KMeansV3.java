package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.api.API;
import water.api.ClusteringModelParametersSchema;
import water.api.KeyV3;

public class KMeansV3 extends ClusteringModelBuilderSchema<KMeans,KMeansV3,KMeansV3.KMeansParametersV3> {

  public static final class KMeansParametersV3 extends ClusteringModelParametersSchema<KMeansParameters, KMeansParametersV3> {
    static public String[] own_fields = new String[] { "user_points", "max_iterations", "standardize", "seed", "init" };

    // Input fields
    @API(help = "User-specified points", required = false)
    public KeyV3.FrameKeyV3 user_points;

    @API(help="Maximum training iterations")
    public int max_iterations;        // Max iterations

    @API(help = "Standardize columns", level = API.Level.secondary, gridable = true)
    public boolean standardize = true;

    @API(help = "RNG Seed", level = API.Level.expert /* tested, works: , dependsOn = {"k", "max_iterations"} */, gridable = true)
    public long seed;

    @API(help = "Initialization mode", values = { "Random", "PlusPlus", "Furthest", "User" }, gridable = true) // TODO: pull out of enum class. . .
    public KMeans.Initialization init;
  }
}
