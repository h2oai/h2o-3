package water.api.schemas3;

import water.api.API;
import water.api.FramesHandler.Frames;
import water.fvec.Frame;

public class FramesListV3 extends RequestSchemaV3<Frames, FramesListV3> {

    // Input fields
    @API(help="Name of Frame of interest", json=false)
    public KeyV3.FrameKeyV3 frame_id;

    // Output fields
    @API(help="Frames", direction=API.Direction.OUTPUT)
    public FrameBaseV3[] frames;


    public FramesListV3 fillFromImplWithSynopsis(Frames f) {
    this.frame_id = new KeyV3.FrameKeyV3(f.frame_id);

    if (f.frames != null) {
      this.frames = new FrameSynopsisV3[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameSynopsisV3(frame);
      }
    }
    return this;
  }
}