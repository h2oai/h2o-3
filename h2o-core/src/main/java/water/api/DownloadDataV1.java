package water.api;

import water.*;
import java.io.InputStream;

public class DownloadDataV1 extends Schema<Iced, DownloadDataV1> {

  // Input fields
  @API(help="Key of file to download", required=true) Key key;
  @API(help="Emit double values in a machine readable lossless format with Double.toHexString().") boolean hex_string;

  // Output
  @API(help="CSV Stream", direction=API.Direction.OUTPUT) InputStream csv;
  @API(help="Suggested Filename", direction=API.Direction.OUTPUT) String filename;
}
