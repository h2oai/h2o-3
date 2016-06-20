package water.api;

import water.Iced;

/**
 * Created by tomas on 4/5/16.
 * API for inhale of sparse data.
 */
public class ParseSVMLightV3 extends SchemaV3<Iced, ParseSVMLightV3> {
  // Input fields
  @API(help = "Final frame name", required = false)
  KeyV3.FrameKeyV3 destination_frame;  // TODO: for now this has to be a Key, not a Frame, because it doesn't exist yet.

  @API(help = "Source frames", required = true)
  KeyV3.FrameKeyV3[] source_frames;

}
