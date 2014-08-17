package water.api;

import water.*;
import water.api.CascadeHandler.Cascade;

public class CascadeV1 extends Schema<Cascade, CascadeV1> {

  // Input fields
  @API(help="An Abstract Syntax Tree.")
  String ast;

  // Output
  @API(help="A Key is returned from the execution of the R expression.")
  Key key;

  @Override public Cascade createImpl() {
    Cascade c = new Cascade();
    if (ast.equals("")) throw H2O.fail("No ast supplied! Nothing to do.");
    c._ast = ast;
    return c;
  }

  @Override public CascadeV1 fillFromImpl(Cascade cascade) {
    ast = cascade._ast;
    return this;
  }
}