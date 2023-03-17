package hex.schemas;

import hex.tree.sdt.SDT;
import hex.tree.sdt.SDTModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class SDTV3 extends ModelBuilderSchema<
        SDT,
        SDTV3, 
        SDTV3.SDTParametersV3> {

    public static final class SDTParametersV3 extends ModelParametersSchemaV3<SDTModel.SDTParameters, SDTParametersV3> {
        static public String[] fields = new String[]{
                "model_id",
                "training_frame",
                "ignored_columns",
                "ignore_const_cols",
                "categorical_encoding",
                "response_column",
                "seed",
                // SDT specific
                "max_depth",
        };

        @API(help = "Max depth of tree.", gridable = true)
        public int max_depth;

        @API(help = "Seed.", gridable = true)
        public long seed;
    }
}
