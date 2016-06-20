package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3.FrameKeyV3;

public class DownloadDataV3 extends SchemaV3<Iced, DownloadDataV3> {

  // Input fields
  @API(help="Frame to download", required=true)
  public FrameKeyV3 frame_id;

  @API(help="Emit double values in a machine readable lossless format with Double.toHexString().")
  public boolean hex_string;

  // Output
  @API(help="CSV Stream", direction=API.Direction.OUTPUT)
  public String csv;

  @API(help="Suggested Filename", direction=API.Direction.OUTPUT)
  public String filename;
}
