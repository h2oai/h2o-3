package water.api;

import water.Iced;
import water.api.KeyV3.FrameKeyV3;

public class DownloadDataV3 extends SchemaV3<Iced, DownloadDataV3> {

  // Input fields
  @API(help="Frame to download", required=true)
  FrameKeyV3 frame_id;
  @API(help="Emit double values in a machine readable lossless format with Double.toHexString().") boolean hex_string;

  // Output
  @API(help="CSV Stream", direction=API.Direction.OUTPUT) String csv;
  @API(help="Suggested Filename", direction=API.Direction.OUTPUT) String filename;
}
