package hex.schemas;

import hex.tree.isoforextended.ExtendedIsolationForest;
import hex.tree.isoforextended.ExtendedIsolationForestModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class ExtendedIsolationForestV3 extends ModelBuilderSchema<
        ExtendedIsolationForest,
        ExtendedIsolationForestV3, 
        ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3> {

    public static final class ExtendedIsolationForestParametersV3 extends ModelParametersSchemaV3<ExtendedIsolationForestModel.ExtendedIsolationForestParameters, ExtendedIsolationForestParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "ignored_columns",
                "ignore_const_cols",
                "categorical_encoding",
                "score_each_iteration",
                "score_tree_interval",

                // Extended Isolation Forest specific
                "ntrees",
                "sample_size",
                "extension_level",
                "seed",
        };

        @API(help = "Number of Extended Isolation Forest trees.", gridable = true)
        public int ntrees;

        @API(help = "Number of randomly sampled observations used to train each Extended Isolation Forest tree.", gridable = true)
        public int sample_size;

        @API(help = "Maximum is N - 1 (N = numCols). Minimum is 0. Extended Isolation Forest " +
                "with extension_Level = 0 behaves like Isolation Forest.", gridable = true)
        public int extension_level;

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;

        @API(help="Score the model after every so many trees. Disabled if set to 0.", level = API.Level.secondary, gridable = false)
        public int score_tree_interval;
    }
}
