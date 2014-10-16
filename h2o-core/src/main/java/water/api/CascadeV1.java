package water.api;

import water.*;
import water.api.CascadeHandler.Cascade;

public class CascadeV1 extends Schema<Cascade, CascadeV1> {

  // Input fields
  @API(help="An Abstract Syntax Tree.")
  String ast;

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


  @Override public Cascade createImpl() {
    Cascade c = new Cascade();
    if (ast.equals("")) throw H2O.fail("No ast supplied! Nothing to do.");
    c._ast = ast;
    return c;
  }

  @Override public CascadeV1 fillFromImpl(Cascade cascade) {
    ast = cascade._ast;
    key = cascade._key;
    num_rows = cascade._num_rows;
    num_cols = cascade._num_cols;
    scalar = cascade._scalar;
    funstr = cascade._funstr;
    result = cascade._result;
    col_names = cascade._col_names;
    string = cascade._string;
    exception = cascade._error;
    return this;
  }
}
