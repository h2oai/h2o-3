package water.api;

import hex.CreateFrame;
import water.Job;
import water.fvec.Frame;

public class CreateFrameHandler extends Handler {

  public CreateFrameV3 run(int version, CreateFrameV3 cf) {
    CreateFrame cfr = new CreateFrame(cf.dest.key());
    cf.fillImpl(cfr);
    Job<Frame> job = cfr.execImpl(); //non-blocking -> caller has to check Job progress
    CreateFrameV3 cfv3 = (CreateFrameV3)Schema.schema(version, CreateFrame.class).fillFromImpl(cfr);
    cfv3.key  = new KeyV3.JobKeyV3  (job._key   );
    cfv3.dest = new KeyV3.FrameKeyV3(job._result);
    return cfv3;
  }
}
