package water.api;

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
    String _ast; // A Lisp-like ast.

    //Outputs
    Key _key;
  }

  CascadeV1 exec(int version, Cascade cascade) {
    return schema(version).fillFromImpl(cascade);
  }

  @Override protected CascadeV1 schema(int version) { return new CascadeV1(); }
}