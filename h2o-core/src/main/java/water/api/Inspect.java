package water.api;

import water.H2O;
import water.Value;
import water.fvec.Frame;
import water.schemas.*;

public class Inspect extends Handler {
  // Inputs
  public Value _val;            // Thing to inspect

  // Outputs
  public Schema _schema;        // Schema for viewing

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  protected Schema inspect() {
    assert _val != null : "schema checks null-ness";

    if( _val.isFrame() ) return (_schema = new FrameV1((Frame)_val.get()));

    // Need a generic viewer here
    throw H2O.unimpl();
  }

  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  // Inspect Schemas are still at V1, unchanged for V2
  @Override protected InspectV1 schema(int version) { return new InspectV1(); }
}

