package water.api;

import hex.CreateFrame;
import water.Job;
import water.exceptions.H2OIllegalArgumentException;

public class CreateFrameHandler extends Handler {

  public JobV3 run(int version, CreateFrameV3 cf) {
    if (cf.dest == null) throw new H2OIllegalArgumentException("No frame name provided.");
    CreateFrame cfr = new CreateFrame(cf.dest.key());
    cf.fillImpl(cfr);
    return (JobV3)Schema.schema(version, Job.class).fillFromImpl(cfr.execImpl());
  }
}
