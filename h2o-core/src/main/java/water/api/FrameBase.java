package water.api;

import water.Iced;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.schemas3.FramesV3;
import water.api.schemas3.SchemaV3;
import water.fvec.Frame;

/**
 * The minimal amount of information on a Frame.
 * @see FramesHandler#list(int, FramesV3)
 */
public class FrameBase<I extends Iced, S extends FrameBase<I, S>> extends SchemaV3<I, S> {
  public transient Frame _fr;         // Avoid a racey update to Key; cached loaded value

  // Input fields
  @API(help="Frame ID",required=true, direction=API.Direction.INOUT)
  public FrameKeyV3 frame_id;

  // Output fields
  @API(help="Total data size in bytes", direction=API.Direction.OUTPUT)
  public long byte_size;

  @API(help="Is this Frame raw unparsed data?", direction=API.Direction.OUTPUT)
  public boolean is_text;
}
