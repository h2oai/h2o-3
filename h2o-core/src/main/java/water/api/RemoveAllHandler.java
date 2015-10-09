package water.api;

import water.*;
import water.util.Log;

import java.util.Set;

public class RemoveAllHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveAllV3 remove(int version, RemoveAllV3 u) {
    Log.info("Removing all objects");
    Futures fs = new Futures();
    for( Job j : Job.jobs() ) { j.cancel(); j.remove(fs); }
    fs.blockForPending();
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new MRTask(){
      @Override public byte priority() { return H2O.GUI_PRIORITY; }
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    Log.info("Finished removing objects");
    return u;
  }
}
