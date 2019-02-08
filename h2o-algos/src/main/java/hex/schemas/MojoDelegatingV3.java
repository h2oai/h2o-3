package hex.schemas;

import hex.mojo.MojoDelegating;
import hex.mojo.MojoDelegatingModelParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class MojoDelegatingV3 extends ModelBuilderSchema<MojoDelegating, MojoDelegatingV3, MojoDelegatingV3.MojoDelegatingParametersV3> {

    public static final class MojoDelegatingParametersV3 extends ModelParametersSchemaV3<MojoDelegatingModelParameters, MojoDelegatingParametersV3> {
        public static final String[] fields = new String[]{
                "mojo_key"
        };
        
        @API(required = true, direction = API.Direction.INPUT, level = API.Level.critical, help = "Key to an uploaded MOJO archive frame")
        public String mojo_key;
    }
}
