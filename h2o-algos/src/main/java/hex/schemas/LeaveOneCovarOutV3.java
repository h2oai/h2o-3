package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class LeaveOneCovarOutV3 extends SchemaV3<Iced, LeaveOneCovarOutV3> {

    @API(help="input supervised model", required = true, direction = API.Direction.INPUT)
    public KeyV3.ModelKeyV3 model;

    @API(help="input frame",required = true, direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 frame;

    @API(help="Value to replace columns in LOCO analysis",required = false, direction = API.Direction.INPUT)
    public String replace_val;

    @API(help="Destination id for this LOCO job; auto-generated if not specified.", direction = API.Direction.INOUT, required = false)
    public String loco_frame_id;

}
