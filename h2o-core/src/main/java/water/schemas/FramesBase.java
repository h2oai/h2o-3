package water.schemas;

import water.H2O;
import water.Key;
import water.api.FramesHandler;

// public class FramesBase<H extends Handler<H,S>, S extends Schema<H,S>> extends Schema<H, S> {
public class FramesBase extends Schema<FramesHandler, FramesBase> {
  // Input fields
  @API(help="Key to inspect",validation="/*this input is NOT required*/")
  Key key;

  // Output fields
  @API(help="Frames")
  FrameV1[] frames; // TODO: create interface or superclass
    // Non-version-specific filling into the handler
    public FramesBase fillInto( FramesHandler h ) {
        throw H2O.fail();
    }

    // Version&Schema-specific filling from the handler
    public FramesBase fillFrom( FramesHandler h ) {
        return this;
    }
}
