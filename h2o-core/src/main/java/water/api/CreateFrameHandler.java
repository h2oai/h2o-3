package water.api;

import hex.CreateFrame;
import water.Job;
import water.Key;

public class CreateFrameHandler extends Handler {

  public JobV3 run(int version, CreateFrameV3 cf) {
//    if (cf.dest == null) throw new H2OIllegalArgumentException("No frame name provided.");
    if (cf.dest == null) {
      cf.dest = new KeyV3.FrameKeyV3();
      cf.dest.name = Key.rand();
    }


    CreateFrame cfr = new CreateFrame(cf.dest.key());
    cf.fillImpl(cfr);
    return (JobV3)SchemaServer.schema(version, Job.class).fillFromImpl(cfr.execImpl());
  }
}
