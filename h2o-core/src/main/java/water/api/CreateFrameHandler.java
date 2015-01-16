package water.api;

import hex.CreateFrame;
import water.Job;

public class CreateFrameHandler extends Handler {

  public JobV2 run(int version, CreateFrameV2 cf) {
    CreateFrame cfr = new CreateFrame();
    cf.fillImpl(cfr);
    cfr.execImpl();
    return (JobV2)Schema.schema(version, Job.class).fillFromImpl(cfr);
  }

  @Override protected int min_ver() {
    return 2;
  }
  @Override protected int max_ver() {
    return Integer.MAX_VALUE;
  }
}
