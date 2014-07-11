package water.api;

import water.api.FramesHandler.Frames;
import water.Key;
import water.fvec.Frame;

abstract class FramesBase extends Schema<Frames, FramesBase> {
  // Input fields
  @API(help="Key of Frame of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  Key key;

  @API(help="Name of column of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  String column;

  // Output fields
  @API(help="Frames")
  FrameV2[] frames; // TODO: create interface or superclass (e.g., FrameBase) for FrameV2

  // Non-version-specific filling into the impl
  @Override public Frames createImpl() {
    Frames f = new Frames();
    f.key = this.key;
    f.column = this.column; // NOTE: this is needed for request handling, but isn't really part of state

    if (null != frames) {
      f.frames = new Frame[frames.length];

      int i = 0;
      for (FrameV2 frame : this.frames) {
        f.frames[i++] = frame._fr;
      }
    }
    return f;
  }

  // TODO: parameterize on the FrameVx Schema class
  @Override public FramesBase fillFromImpl(Frames f) {
    this.key = f.key;
    this.column = f.column; // NOTE: this is needed for request handling, but isn't really partof state; base

    if (null != f.frames) {
      this.frames = new FrameV2[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameV2(frame, 0, 100);
      }
    }
    return this;
  }
}
