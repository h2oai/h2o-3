package water.api;

import hex.CreateFrame;

public class CreateFrameHandler extends Handler {

  public CreateFrameV2 run(int version, CreateFrameV2 cf) {
    CreateFrame cfr = new CreateFrame();
    cf.fillImpl(cfr);
    cfr.execImpl(); //non-blocking -> caller has to check Job progress
    return (CreateFrameV2)Schema.schema(version, CreateFrame.class).fillFromImpl(cfr);
  }
}
