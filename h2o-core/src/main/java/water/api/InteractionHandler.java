package water.api;

import hex.Interaction;
import water.api.schemas3.InteractionV3;
import water.api.schemas3.JobV3;

public class InteractionHandler extends Handler {

  public JobV3 run(int version, InteractionV3 cf) {
    Interaction cfr = new Interaction();
    cf.fillImpl(cfr);
    return new JobV3(cfr.execImpl(cf.dest==null? null : cf.dest.key()));
  }
}
