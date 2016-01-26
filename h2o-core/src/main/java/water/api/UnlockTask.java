package water.api;

import water.*;
import water.util.Log;

public class UnlockTask extends MRTask<UnlockTask> {
  final boolean _quiet;
  public UnlockTask() {super(H2O.GUI_PRIORITY); _quiet = false; }
  public UnlockTask(boolean q) { super(H2O.GUI_PRIORITY); _quiet = q; }
  @Override public void setupLocal() {
    final KeySnapshot.KeyInfo[] kinfo = KeySnapshot.localSnapshot(true)._keyInfos;
    for(KeySnapshot.KeyInfo k:kinfo) {
      if(!k.isLockable()) continue;
      final Value val = DKV.get(k._key);
      if( val == null ) continue;
      final Lockable<?> lockable = val.<Lockable<?>>get();
      final Key[] lockers = lockable._lockers;
      if (lockers != null) {
        // check that none of the locking jobs is still running
        for (Key<Job> locker : lockers) {
          if (locker != null && locker.type() == Key.JOB) {
            final Job job = locker.get();
            if (job != null && job.isRunning())
              throw new UnsupportedOperationException("Cannot unlock all keys since locking jobs are still running.");
          }
        }
        lockable.unlock_all();
        Log.info("Unlocked key '" + k._key + "' from " + lockers.length + " lockers.");
      }
    }
    if (!_quiet) Log.info("All keys are now unlocked.");
  }
}
