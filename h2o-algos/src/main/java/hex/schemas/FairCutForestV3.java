package hex.schemas;

import hex.tree.isoforfaircut.FairCutForest;
import hex.tree.isoforfaircut.FairCutForestModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class FairCutForestV3 extends ModelBuilderSchema<
        FairCutForest,
        FairCutForestV3,
        FairCutForestV3.FairCutForestParametersV3> {

    public static final class FairCutForestParametersV3 extends ModelParametersSchemaV3<FairCutForestModel.FairCutForestParameters, FairCutForestParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "ignored_columns",
                "ignore_const_cols",
                "categorical_encoding",

                // Extended Isolation Forest specific
                "ntrees",
                "sample_size",
                "extension_level",
                "k_planes",
                "seed",
        };

        @API(help = "Number of Fair Cut Forest trees.", gridable = true)
        public int ntrees;

        @API(help = "Number of randomly sampled observations used to train each Fair Cut Forest tree.", gridable = true)
        public int sample_size;

        @API(help = "Maximum is N - 1 (N = numCols). Minimum is 0. Fair Cut Forest " +
                "with extension_Level = 0 behaves like Isolation Forest.", gridable = true)
        public int extension_level;

        @API(help = "Number of randomly generated separating hyperplanes, " +
                "where the best one (that maximizes the pooled gain metric) is selected at each split", gridable = true)
        public int k_planes;

        @API(help = "Seed for pseudo random number generator (if applicable)", gridable = true)
        public long seed;
    }
}
