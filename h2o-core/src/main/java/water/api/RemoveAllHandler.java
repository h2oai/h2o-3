package water.api;

import water.*;
import water.api.schemas3.KeyV3;
import water.api.schemas3.RemoveAllV3;
import water.util.Log;

// Best-effort cluster brain-wipe and reset.
// Useful between unrelated tests.
public class RemoveAllHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveAllV3 remove(int version, RemoveAllV3 u) {
    Futures fs = new Futures();
    // Cancel and remove leftover running jobs
    for( Job j : Job.jobs() ) { j.stop_requested(); j.remove(fs); }
    // Wipe out any and all session info
    if( RapidsHandler.SESSIONS != null ) {
      for(String k: RapidsHandler.SESSIONS.keySet() )
        (RapidsHandler.SESSIONS.get(k)).endQuietly(null);
      RapidsHandler.SESSIONS.clear();
    }
    fs.blockForPending();

    if (u.retained_keys != null && u.retained_keys.length != 0) {
      retainKeys(u.retained_keys);
    } else {
      clearAll();
    }
    Log.info("Finished removing objects");
    return u;
  }


  private void clearAll() {
    Log.info("Removing all objects");
    DKVManager.clear();
  }

  private void retainKeys(final KeyV3[] retained_keys) {
    Log.info(String.format("Removing all objects, except for %d provided key(s)", retained_keys.length));
    final Key[] retainedKeys;
    if (retained_keys == null) {
      retainedKeys = new Key[0];
    } else {
      retainedKeys = new Key[retained_keys.length];
      for (int i = 0; i < retainedKeys.length; i++) {
        if (retained_keys[i] == null) throw new IllegalArgumentException("An attempt to retain a 'null' key detected. Cleaning operation aborted.");
        retainedKeys[i] = retained_keys[i].key();
      }
    }
    DKVManager.retain(retainedKeys);
  }
}
