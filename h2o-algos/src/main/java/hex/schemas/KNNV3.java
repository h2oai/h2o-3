package hex.schemas;

import hex.DistanceType;
import hex.knn.KNN;
import hex.knn.KNNModel;
import water.api.API;
import water.api.schemas3.FrameV3;
import water.api.schemas3.ModelParametersSchemaV3;


public class KNNV3 extends ModelBuilderSchema<KNN, KNNV3, KNNV3.KNNParametersV3> {
    public static final class KNNParametersV3 extends ModelParametersSchemaV3<KNNModel.KNNParameters, KNNParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "response_column",
                "id_column",
                "ignored_columns",
                "ignore_const_cols",
                "seed",
                "max_runtime_secs",
                "categorical_encoding",
                "distribution",
                "custom_metric_func",
                "gainslift_bins",
                "auc_type",
                "k",
                "distance"
        };

        @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
                is_member_of_frames = {"training_frame"},
                is_mutually_exclusive_with = {"ignored_columns"},
                help = "Identify each record column.")
        public FrameV3.ColSpecifierV3 id_column;
        
        @API(help = "RNG Seed", level = API.Level.secondary, gridable = true)
        public long seed;

        @API(help = "Number of nearest neighbours", level = API.Level.secondary, gridable = true)
        public int k;

        @API(help = "Distance type", level = API.Level.secondary, gridable = true, values = { "AUTO", "euclidean", "manhattan", "cosine"})
        public DistanceType distance;
    }
}
