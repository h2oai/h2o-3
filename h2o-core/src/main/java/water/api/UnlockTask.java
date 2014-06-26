package water.api;

import water.*;
import water.util.Log;

public class UnlockTask extends MRTask<UnlockTask> {
  @Override public byte priority() { return H2O.GUI_PRIORITY; }
  @Override public void setupLocal() {
    final KeySnapshot.KeyInfo[] kinfo = KeySnapshot.localSnapshot(true)._keyInfos;
    for(KeySnapshot.KeyInfo k:kinfo) {
      if(!k.isLockable()) continue;
      final Value val = DKV.get(k._key);
      if( val == null ) continue;
      final Object obj = val.rawPOJO();
      if( obj == null ) continue; //need to have a POJO to be locked
      final Lockable<?> lockable = (Lockable<?>)(obj);
      final Key[] lockers = ((Lockable) obj)._lockers;
      if (lockers != null) {
        // check that none of the locking jobs is still running
        for (Key locker : lockers) {
          if (locker != null && locker.type() == Key.JOB) {
            final Value jobv = DKV.get(locker);
            final Job job = jobv == null ? null : (Job)jobv.get();
            if (job != null && job.isRunning())
              throw new UnsupportedOperationException("Cannot unlock all keys since locking jobs are still running.");
          }
        }
        lockable.unlock_all();
        Log.info("Unlocked key '" + k._key + "' from " + lockers.length + " lockers.");
      }
    }
    Log.info("All keys are now unlocked.");
  }
}
