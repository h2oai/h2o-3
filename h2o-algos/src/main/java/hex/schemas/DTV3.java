package hex.schemas;

import hex.tree.dt.DT;
import hex.tree.dt.DTModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class DTV3 extends ModelBuilderSchema<
        DT,
        DTV3,
        DTV3.DTParametersV3> {

    public static final class DTParametersV3 extends ModelParametersSchemaV3<DTModel.DTParameters, DTParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "ignored_columns",
                "ignore_const_cols",
                "categorical_encoding",
                "response_column",
                "seed",
                "distribution",
                // SDT specific
                "max_depth",
                "min_rows"
        };

        @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
                help = "Seed for random numbers (affects sampling)")
        public long seed;

        @API(help = "Max depth of tree.", gridable = true)
        public int max_depth;

        @API(help = "Fewest allowed (weighted) observations in a leaf.", gridable = true)
        public int min_rows;
    }
}
