package water.api;

import water.*;

public class RapidsV1 extends Schema<RapidsHandler.Rapids, RapidsV1> {

  // Input fields
  @API(help="An Abstract Syntax Tree.")
  String ast;
  @API(help="An array of function defintions.")
  String[] funs;

  // Output
  @API(help="Parsing error, if any"   , direction=API.Direction.OUTPUT) String exception;
  @API(help="Result key"              , direction=API.Direction.OUTPUT) Key key;
  @API(help="Rows in Frame result"    , direction=API.Direction.OUTPUT) long num_rows;
  @API(help="Columns in Frame result" , direction=API.Direction.OUTPUT) int  num_cols;
  @API(help="Scalar result"           , direction=API.Direction.OUTPUT) double scalar;
  @API(help="Function result"         , direction=API.Direction.OUTPUT) String funstr;
  @API(help="Column Names"            , direction=API.Direction.OUTPUT) String[] col_names;
  @API(help="String result"           , direction=API.Direction.OUTPUT) String string;
  // Pretty-print of result.  For Frames, first 10 rows.  For scalars, just the
  // value.  For functions, the pretty-printed AST.
  @API(help="String result"           , direction=API.Direction.OUTPUT) String result;

//  TODO @API(help="Array of Column Summaries.") Inspect2.ColSummary cols[];

  @Override public RapidsHandler.Rapids createImpl() {
    RapidsHandler.Rapids c = new RapidsHandler.Rapids();
    if ((ast == null || ast.equals("")) && funs == null) return null;
    c._ast = ast;
    c._funs = funs;
    return c;
  }

  @Override public RapidsV1 fillFromImpl(RapidsHandler.Rapids rapids) {
    ast       = rapids._ast;
    funs      = rapids._funs;
    key       = rapids._key;
    num_rows  = rapids._num_rows;
    num_cols  = rapids._num_cols;
    scalar    = rapids._scalar;
    funstr    = rapids._funstr;
    result    = rapids._result;
    col_names = rapids._col_names;
    string    = rapids._string;
    exception = rapids._error;
    return this;
  }
}
