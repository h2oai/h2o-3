package water.api;

//import com.google.gson.Gson;
//import com.google.gson.JsonElement;
import water.*;
import water.api.CascadeHandler.Cascade;

public class CascadeV1 extends Schema<Cascade, CascadeV1> {

  // Input fields
  @API(help="An R-like expression.")
  String expr;

  @API(help="An Abstract Syntax Tree.")
  String ast;

  // Output
  @API(help="A Key is returned from the execution of the R expression.")
  Key key;

  @Override public Cascade createImpl() {
    Cascade c = new Cascade();

    // TODO: R-like expressions unsupported for now
    if (!expr.equals("")) throw H2O.unimpl();

    if (ast.equals("")) throw H2O.fail("No ast supplied! Nothing to do.");
    // Try to parse the String ast into a JsonObject
//    JsonElement el = (new Gson()).fromJson(ast, JsonElement.class);
//
//    c._ast = el.getAsJsonObject();
    c._expr = expr;
    c._json_ast = ast;

    return c;
  }

  @Override public CascadeV1 fillFromImpl(Cascade cascade) {
    expr = cascade._expr;
    ast  = cascade._json_ast;
    return this;
  }
}
