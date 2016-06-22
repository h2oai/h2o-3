package water.api;

import water.Futures;
import water.H2O;
import water.Job;
import water.MRTask;
import water.api.schemas3.RemoveAllV3;
import water.util.Log;

// Best-effort cluster brain-wipe and reset.
// Useful between unrelated tests.
public class RemoveAllHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveAllV3 remove(int version, RemoveAllV3 u) {
    Log.info("Removing all objects");
    Futures fs = new Futures();
    // Cancel and remove leftover running jobs
    for( Job j : Job.jobs() ) { j.stop_requested(); j.remove(fs); }
    // Wipe out any and all session info
    if( InitIDHandler.SESSIONS != null ) {
      for(String k: InitIDHandler.SESSIONS.keySet() )
        (InitIDHandler.SESSIONS.get(k)).endQuietly(null);
      InitIDHandler.SESSIONS.clear();
    }
    fs.blockForPending();
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new MRTask(H2O.MIN_HI_PRIORITY){
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    // Wipe the backing store without regard as well
    H2O.getPM().getIce().cleanUp();
    Log.info("Finished removing objects");
    return u;
  }
}