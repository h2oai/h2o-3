package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.fvec.Frame;

import java.util.Map;

public class RapidsMapFrameV3 extends RapidsSchemaV3<Iced,RapidsMapFrameV3> {

  // Output fields
  @API(help="Frames", direction=API.Direction.OUTPUT)
  public RapidsFrameV3[] frames;

  @API(help="Keys", direction=API.Direction.OUTPUT)
  public RapidsStringsV3 teColumns;

  public RapidsMapFrameV3() {}

  public RapidsMapFrameV3(Map<String, Frame> fr) {
    teColumns = new RapidsStringsV3(fr.keySet().toArray(new String[]{}));
    int i = 0;
    Frame[] framesFromMap = fr.values().toArray(new Frame[]{});
    frames = new RapidsFrameV3[framesFromMap.length];
    for (Frame frame : framesFromMap) {
      this.frames[i++] = new RapidsFrameV3(frame);
    }
  }
}
