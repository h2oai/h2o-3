package water.api;

import hex.Interaction;

public class InteractionHandler extends Handler {

  public JobV3 run(int version, InteractionV3 cf) {
    Interaction cfr = new Interaction();
    cf.fillImpl(cfr);
    return (JobV3)SchemaServer.schema(version, water.Job.class).fillFromImpl(cfr.execImpl(cf.dest==null?null:cf.dest.key
        ()));
  }
}
