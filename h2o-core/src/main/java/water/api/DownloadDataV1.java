package water.api;

import water.*;
import water.api.DownloadDataHandler.DownloadData;
import java.io.InputStream;

public class DownloadDataV1 extends Schema<DownloadData, DownloadDataV1> {

  // Input fields
  @API(help="Key of file to download", required=true) Key key;
  @API(help="Emit double values in a machine readable lossless format with Double.toHexString().") boolean hex_string;

  // Output
  @API(help="CSV Stream", direction=API.Direction.OUTPUT) InputStream csv;
  @API(help="Suggested Filename", direction=API.Direction.OUTPUT) String filename;

  @Override public DownloadData createImpl() {
    DownloadData dd = new DownloadData();
    dd.src_key = key;
    return dd;
  }

  @Override public DownloadDataV1 fillFromImpl(DownloadData dd) {
    key = dd.src_key;
    hex_string = dd.hex_string;
    csv = dd.csv;
    filename = dd.filename;
    return this;
  }

  @Override public DownloadData fillFromSchema() {
    DownloadData dd = new DownloadData();
    dd.filename = filename;
    dd.csv = csv;
    return dd;
  }
}
