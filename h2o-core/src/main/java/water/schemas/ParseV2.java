package water.schemas;

import water.api.Parse;
import water.api.Handler;
import water.H2O;
import water.Key;
import water.H2ONode;

public class ParseV2 extends Schema {

  // Input fields
  private final Inputs _ins = new Inputs();
  private static class Inputs {
    @API(help="Final hex key name",validation="/*this input is required*/")
    Key hex;

    @API(help="Source keys",validation="/*this input is required*/",dependsOn={"hex"})
    Key[] srcs;

    @API(help="Delete input key after parse")
    boolean delete_on_done;

    @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
    boolean blocking;
  }

  // Output fields
  private final Outputs _outs = new Outputs();
  private static class Outputs {
    @API(help="Job Key")
    Key job;
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ParseV2 fillInto( Handler h ) {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public ParseV2 fillFrom( Handler h ) {
    throw H2O.unimpl();
  }

}
