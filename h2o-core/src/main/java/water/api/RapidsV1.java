package water.api;

import water.*;

public class RapidsV1 extends Schema<Iced, RapidsV1> {
  // Input fields
  @API(help="An Abstract Syntax Tree."            , direction=API.Direction.INPUT) String ast;
  @API(help="An array of function definitions."   , direction=API.Direction.INPUT) String[] funs;
  @API(help="A pointer to an object of type Raft.", direction=API.Direction.INPUT) Key astKey;

  // Output
  @API(help="Parsing error, if any"  , direction=API.Direction.OUTPUT) String exception;
  @API(help="Result key"             , direction=API.Direction.OUTPUT) Key key;
  @API(help="Rows in Frame result"   , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result", direction=API.Direction.OUTPUT) int  num_cols;
  @API(help="Scalar result"          , direction=API.Direction.OUTPUT) double scalar;
  @API(help="Function result"        , direction=API.Direction.OUTPUT) String funstr;
  @API(help="Column Names"           , direction=API.Direction.OUTPUT) String[] col_names;
  @API(help="String result"          , direction=API.Direction.OUTPUT) String string;
  @API(help="result"                 , direction=API.Direction.OUTPUT) String result;
  @API(help="Raft ast"               , direction=API.Direction.OUTPUT) String raft_ast;
  @API(help="Raft key"               , direction=API.Direction.OUTPUT) Key    raft_key;

//  TODO @API(help="Array of Column Summaries.") Inspect2.ColSummary cols[];

}
