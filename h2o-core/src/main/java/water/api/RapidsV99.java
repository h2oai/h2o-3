package water.api;

import water.Iced;

public class RapidsV99 extends RequestSchema<Iced, RapidsV99> {

  static final int ARY   =0;
  static final int NUM   =1;
  static final int STR   =2;
  static final int ARYNUM=3;
  static final int ARYSTR=4;

  // Input fields
  @API(help="An Abstract Syntax Tree."            , direction=API.Direction.INPUT) String ast;
  @API(help="An array of function definitions."   , direction=API.Direction.INPUT) String fun;
  @API(help="A pointer to a Frame"                , direction=API.Direction.INPUT)
  KeyV3 ast_key;

  // Output
  @API(help="Parsing error, if any"  , direction=API.Direction.OUTPUT) String error;
  @API(help="Result key"             , direction=API.Direction.OUTPUT) KeyV3.FrameKeyV3 key;
  @API(help="Rows in Frame result"   , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT) int  num_cols;
  @API(help="Scalar result"          , direction=API.Direction.OUTPUT) double scalar;
  @API(help="Function result"        , direction=API.Direction.OUTPUT) String funstr;
  @API(help="Column Names"           , direction=API.Direction.OUTPUT) String[] col_names;
  @API(help="String result"          , direction=API.Direction.OUTPUT) String string;
  @API(help="result"                 , direction=API.Direction.OUTPUT) String result;
  @API(help="Was evaluated"          , direction=API.Direction.OUTPUT) boolean evaluated;
  @API(help="Head of a Frame result" , direction=API.Direction.OUTPUT) String[][] head;
  @API(help="Result Type."           , direction=API.Direction.OUTPUT) int result_type;
  @API(help="Vec keys for key result", direction=API.Direction.OUTPUT) KeyV3.VecKeyV3[] vec_ids;
}
