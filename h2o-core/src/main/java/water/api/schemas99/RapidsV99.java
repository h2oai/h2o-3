package water.api.schemas99;

import water.Iced;
import water.api.API;
import water.api.schemas3.SchemaV3;
import water.api.schemas3.KeyV3;

public class RapidsV99 extends SchemaV3<Iced, RapidsV99> {
  // Input fields
  @API(help="An Abstract Syntax Tree.", direction=API.Direction.INPUT)
  public String ast;

  // Output.  Only one of these 5 results is returned; the rest are null - and
  // this is how the caller tells about which result is valid.
  @API(help="Parsing error, if any", direction=API.Direction.OUTPUT)
  public String error;

  @API(help="Scalar result", direction=API.Direction.OUTPUT)
  public double scalar;

  @API(help="Function result", direction=API.Direction.OUTPUT)
  public String funstr;

  @API(help="String result", direction=API.Direction.OUTPUT)
  public String string;

  @API(help="Result key", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 key;

  @API(help="Rows in Frame result", direction=API.Direction.OUTPUT)
  public long num_rows;

  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT)
  public int num_cols;
}
