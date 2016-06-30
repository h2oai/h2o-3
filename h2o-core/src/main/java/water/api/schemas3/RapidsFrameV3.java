package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.fvec.Frame;

public class RapidsFrameV3 extends RapidsSchemaV3<Iced,RapidsFrameV3> {
  @API(help="Frame result", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 key;

  @API(help="Rows in Frame result", direction=API.Direction.OUTPUT)
  public long num_rows;

  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT)
  public int num_cols;

  public RapidsFrameV3() {}

  public RapidsFrameV3(Frame fr) {
    key = new KeyV3.FrameKeyV3(fr._key);
    num_rows = fr.numRows();
    num_cols = fr.numCols();
  }
}
