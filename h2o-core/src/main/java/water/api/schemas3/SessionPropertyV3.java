package water.api.schemas3;

import water.Iced;
import water.api.API;

public class SessionPropertyV3 extends RequestSchemaV3<Iced, SessionPropertyV3> {

    @API(help="Session ID", direction = API.Direction.INOUT)
    public String session_key;

    @API(help="Property Key", direction = API.Direction.INOUT)
    public String key;

    @API(help="Property Value", direction = API.Direction.INOUT)
    public String value;

}
