package water.api;

import hex.CreateFrame;

public class CreateFrameHandler extends Handler {

  public CreateFrameV2 run(int version, CreateFrameV2 cf) {
    CreateFrame cfr = new CreateFrame();
    cf.fillImpl(cfr);
    cfr.execImpl();
    return cf;
  }

  @Override protected int min_ver() {
    return 2;
  }
  @Override protected int max_ver() {
    return Integer.MAX_VALUE;
  }
}
