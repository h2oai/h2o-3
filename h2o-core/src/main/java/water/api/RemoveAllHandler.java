package water.api;

import water.*;
import water.util.Log;

import java.util.Set;

// Best-effort cluster brain-wipe and reset.
// Useful between unrelated tests.
public class RemoveAllHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveAllV3 remove(int version, RemoveAllV3 u) {
    Log.info("Removing all objects");
    Futures fs = new Futures();
    // Cancel and remove leftover running jobs
    for( Job j : Job.jobs() ) { j.cancel(); j.remove(fs); }
    // Wipe out any and all session info
    if( InitIDHandler.SESSION != null ) {
      InitIDHandler.SESSION.endQuietly(null);
      InitIDHandler.SESSION = null;
    }
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
