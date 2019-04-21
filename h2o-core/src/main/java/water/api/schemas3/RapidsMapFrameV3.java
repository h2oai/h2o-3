package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.fvec.Frame;

import java.util.Map;

public class RapidsMapFrameV3 extends RapidsSchemaV3<Iced,RapidsMapFrameV3> {

  @API(help="Frames", direction=API.Direction.OUTPUT)
  public RapidsFrameV3[] frames;

  @API(help="Keys of the map", direction=API.Direction.OUTPUT)
  public RapidsStringsV3 map_keys;

  public RapidsMapFrameV3() {}

  public RapidsMapFrameV3(Map<String, Frame> fr) {
    map_keys = new RapidsStringsV3(fr.keySet().toArray(new String[]{}));
    int i = 0;
    Frame[] framesFromMap = fr.values().toArray(new Frame[]{});
    frames = new RapidsFrameV3[framesFromMap.length];
    for (Frame frame : framesFromMap) {
      this.frames[i++] = new RapidsFrameV3(frame);
    }
  }
}
