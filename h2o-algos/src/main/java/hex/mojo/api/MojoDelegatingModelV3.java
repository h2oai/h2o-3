package hex.mojo.api;

import water.Iced;
import water.api.API;
import water.api.schemas3.SchemaV3;

public class MojoDelegatingModelV3 extends SchemaV3<Iced, MojoDelegatingModelV3> {
    
    @API(required = true, direction = API.Direction.INPUT, level = API.Level.secondary, help = "Key to an uploaded MOJO archive")
    public String mojo_file_key;
    
}
