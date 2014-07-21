package water.api;

import com.google.gson.JsonObject;
import water.H2O;
import water.Iced;
import water.Key;
import water.api.CascadeHandler.Cascade;

class CascadeHandler extends Handler<Cascade, CascadeV1>{

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @Override protected void compute2() { throw H2O.fail(); }

  protected static final class Cascade extends Iced {
    // Inputs
    JsonObject _ast;        // The tree to be walked by AST2IR.

    String _expr;           // An R-like expression.
    String _json_ast;       // An input string of valid JSON.

    //Outputs
    Key _key;
  }

  @Override protected CascadeV1 schema(int version) { return new CascadeV1(); }
}
