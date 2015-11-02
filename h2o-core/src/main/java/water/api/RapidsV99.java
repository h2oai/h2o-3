package water.api;

import water.Iced;

public class RapidsV99 extends RequestSchema<Iced, RapidsV99> {
  // Input fields
  @API(help="An Abstract Syntax Tree.", direction=API.Direction.INPUT) String ast;

  // Output.  Only one of these 5 results is returned; the rest are null - and
  // this is how the caller tells about which result is valid.
  @API(help="Parsing error, if any"  , direction=API.Direction.OUTPUT) String error;
  @API(help="Scalar result"          , direction=API.Direction.OUTPUT) double scalar;
  @API(help="Function result"        , direction=API.Direction.OUTPUT) String funstr;
  @API(help="String result"          , direction=API.Direction.OUTPUT) String string;
  @API(help="Result key"             , direction=API.Direction.OUTPUT) KeyV3.FrameKeyV3 key;
  @API(help="Rows in Frame result"   , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT) int  num_cols;
}
