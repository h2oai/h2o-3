package water.api;

import water.Iced;

public class RapidsV1 extends Schema<Iced, RapidsV1> {

  // Input fields
  @API(help="An Abstract Syntax Tree."            , direction=API.Direction.INPUT) String ast;
  @API(help="An array of function definitions."   , direction=API.Direction.INPUT) String[] funs;
  @API(help="A pointer to a Frame or Raft."       , direction=API.Direction.INPUT) KeyV1 ast_key;

  // Output
  @API(help="Parsing error, if any"  , direction=API.Direction.OUTPUT) String error;
  @API(help="Result key"             , direction=API.Direction.OUTPUT) KeyV1.FrameKeyV1 key;
  @API(help="Rows in Frame result"   , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT) int  num_cols;
  @API(help="Scalar result"          , direction=API.Direction.OUTPUT) double scalar;
  @API(help="Function result"        , direction=API.Direction.OUTPUT) String funstr;
  @API(help="Column Names"           , direction=API.Direction.OUTPUT) String[] col_names;
  @API(help="String result"          , direction=API.Direction.OUTPUT) String string;
  @API(help="result"                 , direction=API.Direction.OUTPUT) String result;
  @API(help="Raft ast"               , direction=API.Direction.OUTPUT) String raft_ast;
  @API(help="Raft key"               , direction=API.Direction.OUTPUT) KeyV1  raft_key;
  @API(help="Was evaluated"          , direction=API.Direction.OUTPUT) boolean evaluated;
  @API(help="Head of a Frame result" , direction=API.Direction.OUTPUT) String[][] head;
}