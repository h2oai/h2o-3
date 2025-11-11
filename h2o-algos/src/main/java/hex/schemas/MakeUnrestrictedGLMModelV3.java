package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

/**
 * End point to get unrestricted GLM model when control variables are enabled. Creates a modified copy of the original model.
 */
public class MakeUnrestrictedGLMModelV3 extends SchemaV3<Iced,MakeUnrestrictedGLMModelV3> {

    @API(help = "source model", required = true, direction = API.Direction.INPUT)
    public KeyV3.ModelKeyV3 model;

    @API(help = "destination key", required = false, direction = API.Direction.INPUT)
    public String dest;

}
