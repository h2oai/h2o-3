package hex.schemas;

import hex.klime.KLime;
import hex.klime.KLimeModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class KLimeV3 extends ModelBuilderSchema<KLime, KLimeV3, KLimeV3.KLimeParametersV3> {
  public static final class KLimeParametersV3 extends ModelParametersSchemaV3<KLimeModel.KLimeParameters, KLimeParametersV3> {
    public static String[] fields = new String[] {
            "model_id",
            "training_frame",
            "response_column",
            "ignored_columns",
            "max_k",
            "estimate_k",
            "alpha",
            "min_cluster_size",
            "seed",
    };

    @API(help = "Maximum number of clusters to be considered.", direction = API.Direction.INOUT)
    public int max_k;

    @API(help = "Automatically determine the number of clusters in an unsupervised manner.", direction = API.Direction.INOUT)
    public boolean estimate_k;

    @API(help = "Balance between L1 and L2 regularization. Use alpha=0 to switch off L1 variable selection.", direction = API.Direction.INOUT)
    public double alpha;

    @API(help = "Required minimum cluster size to build a local regression model, smaller clusters will use a global model.", direction = API.Direction.INOUT)
    public int min_cluster_size;

    @API(help = "Seed for pseudo random number generator (if applicable).")
    public long seed;

  }
}
