package water.api;

import water.Iced;
import water.api.KeyV3.FrameKeyV3;
import water.fvec.Frame;

/**
 * The minimal amount of information on a Frame.
 * @see FramesHandler#list(int, FramesV3)
 */
public class FrameBase<I extends Iced, S extends SchemaV3<I, S>> extends SchemaV3<I, S> {
  transient Frame _fr;         // Avoid a racey update to Key; cached loaded value

  // Input fields
  @API(help="Frame ID",required=true, direction=API.Direction.INOUT)
  public FrameKeyV3 frame_id;

  // Output fields
  @API(help="Total data size in bytes", direction=API.Direction.OUTPUT)
  public long byte_size;

  @API(help="Is this Frame raw unparsed data?", direction=API.Direction.OUTPUT)
  public boolean is_text;
}
