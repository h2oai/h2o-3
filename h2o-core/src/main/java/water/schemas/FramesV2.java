package water.schemas;

import water.H2O;
import water.Key;
import water.api.FramesHandler;
import water.api.Handler;

public class FramesV2 extends FramesBase {
  // Version-  and Schema-specific filling into the handler
  @Override public FramesBase fillInto( FramesHandler h ) {
    super.fillInto(h);
    return this;
  }

  // Version-  and Schema-specific filling into the handler
  @Override public FramesBase fillFrom( FramesHandler h ) {
    super.fillFrom(h);
    return this;
  }


}
