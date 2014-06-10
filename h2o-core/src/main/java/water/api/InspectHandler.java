package water.api;

import water.*;
import water.fvec.Frame;

class InspectHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Inputs
  Value _val;            // Thing to inspect

  // Outputs
  Schema _schema;        // Schema for viewing

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  Schema inspect() {
    assert _val != null : "schema checks null-ness";

    if( _val.isKey() ) {        // Peek thru a Key
      _val = DKV.get((Key) _val.get());
      if( _val == null ) throw new IllegalArgumentException("Key is missing");
    }

    if( _val.isFrame() ) return (_schema = new FrameV1((Frame)_val.get()));

    // Need a generic viewer here
    throw H2O.unimpl();
  }

  // Inspect Schemas are still at V1, unchanged for V2
  @Override protected InspectV1 schema(int version) { return new InspectV1(); }
}
