package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

/**
 * End point to get derived GLM model when control variables or remove offset effects features are enabled. Creates a modified copy of the original model.
 */
public class MakeDerivedGLMModelV3 extends SchemaV3<Iced, MakeDerivedGLMModelV3> {

    @API(help = "source model", required = true, direction = API.Direction.INPUT)
    public KeyV3.ModelKeyV3 model;

    @API(help = "destination key", required = false, direction = API.Direction.INPUT)
    public String dest;

    @API(help = "remove offset effects flag", required = false, direction = API.Direction.INPUT)
    public boolean remove_offset_effects;

    @API(help = "remove control variables effects flag", required = false, direction = API.Direction.INPUT)
    public boolean remove_control_variables_effects;
}
