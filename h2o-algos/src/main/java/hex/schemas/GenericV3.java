package hex.schemas;

import hex.mojo.Generic;
import hex.mojo.GenericModelParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class GenericV3 extends ModelBuilderSchema<Generic, GenericV3, GenericV3.GenericParametersV3> {

    public static final class GenericParametersV3 extends ModelParametersSchemaV3<GenericModelParameters, GenericParametersV3> {
        public static final String[] fields = new String[]{
                "mojo_key"
        };
        
        @API(required = true, direction = API.Direction.INPUT, level = API.Level.critical, help = "Key to an uploaded MOJO archive frame", json = false)
        public KeyV3.FrameKeyV3 mojo_key;
    }
}
