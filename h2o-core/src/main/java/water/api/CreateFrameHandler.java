package water.api;

import hex.CreateFrame;
import water.Job;

public class CreateFrameHandler extends Handler {

  public JobV3 run(int version, CreateFrameV3 cf) {
    CreateFrame cfr = new CreateFrame(cf.dest.key());
    cf.fillImpl(cfr);
    return (JobV3)Schema.schema(version, Job.class).fillFromImpl(cfr.execImpl());
  }
}
