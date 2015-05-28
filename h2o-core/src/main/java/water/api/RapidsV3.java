package water.api;

import water.Iced;

public class RapidsV3 extends Schema<Iced, RapidsV3> {

  // Input fields
  @API(help="An Abstract Syntax Tree.", direction=API.Direction.INPUT) String ast;
  @API(help="Frame Result Key"        , direction=API.Direction.INPUT) KeyV3.FrameKeyV3 key;

  // Output.  Only one of these 5 results is returned.
  @API(help="Scalar result"          , direction=API.Direction.OUTPUT) double scalar;
  @API(help="String result"          , direction=API.Direction.OUTPUT) String string;
  @API(help="Function result"        , direction=API.Direction.OUTPUT) String funstr;
  @API(help="Frame result"           , direction=API.Direction.OUTPUT) KeyV3.FrameKeyV3 key;
  @API(help="Rows in Frame result"   , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT) int  num_cols;

  @API(help="Parsing error, if any"  , direction=API.Direction.OUTPUT) String error;
}
