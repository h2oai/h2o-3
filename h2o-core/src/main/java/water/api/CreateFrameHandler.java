package water.api;

import hex.CreateFrame;

public class CreateFrameHandler extends Handler {

  public CreateFrameV3 run(int version, CreateFrameV3 cf) {
    CreateFrame cfr = new CreateFrame();
    cf.fillImpl(cfr);
    cfr.execImpl(); //non-blocking -> caller has to check Job progress
    return (CreateFrameV3)Schema.schema(version, CreateFrame.class).fillFromImpl(cfr);
  }
}
