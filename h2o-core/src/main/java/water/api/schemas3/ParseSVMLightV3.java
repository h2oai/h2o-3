package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 * API for inhale of sparse data.
 */
public class ParseSVMLightV3 extends SchemaV3<Iced, ParseSVMLightV3> {
  // Input fields
  @API(help = "Final frame name", required = false)
  public KeyV3.FrameKeyV3 destination_frame;  // TODO: for now this has to be a Key, not a Frame, because it doesn't exist yet.

  @API(help = "Source frames", required = true)
  public KeyV3.FrameKeyV3[] source_frames;

}
