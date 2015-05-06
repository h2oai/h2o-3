package water.api;

import hex.Interaction;

public class InteractionHandler extends Handler {

  public InteractionV3 run(int version, InteractionV3 cf) {
    Interaction cfr = new Interaction();
    cf.fillImpl(cfr);
    cfr.execImpl(); //blocking
    return (InteractionV3)Schema.schema(version, Interaction.class).fillFromImpl(cfr);
  }
}
