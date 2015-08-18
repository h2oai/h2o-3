package water;
import water.nbhm.NonBlockingHashMapLong;
import water.util.Log;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.DelayQueue;

/**
 * The Thread that looks for RPCs that are timing out
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class UDPTimeOutThread extends Thread {
  public UDPTimeOutThread() { super("UDPTimeout"); }

  // List of "in progress" tasks.  When they time-out we do the time-out action
  // which is possibly a re-send if we suspect a dropped UDP packet, or a
  // fail-out if the target has died.
//  static DelayQueue<RPC> PENDING = new DelayQueue<>();

//  static NonBlockingHashMapLong<RPC> PENDING = new NonBlockingHashMapLong<>();

  // The Run Method.

  // Started by main() on a single thread, handle timing-out UDP packets
  public void run() {
    Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    while( true ) {
      long currentTime = System.currentTimeMillis();
      for(H2ONode n: H2O.CLOUD._memary) {
        if (n == H2O.SELF) continue;
        for (RPC t : n.tasks()) {
          if (H2O.CLOUD.contains(t._target) ||
            // Also retry clients who do not appear to be shutdown
            (t._target._heartbeat._client && t._retry < HeartBeatThread.CLIENT_TIMEOUT)) {
            if (currentTime > (t._started + t._retry) && !t.isDone() && !t._nack) {
              if (++t._resendsCnt % 10 == 0)
                Log.warn("Got " + t._resendsCnt + " resends on task #" + t._tasknum + ", class = " + t._dt.getClass().getSimpleName());
              t.call();
            }
          } else {                // Target is dead, nobody to retry to
            t.cancel(true);
          }
        }
      }
      long timeElapsed = System.currentTimeMillis() - currentTime;
      if(timeElapsed < 1000)
        try {Thread.sleep(1000-timeElapsed);} catch (InterruptedException e) {}
    }
  }
}
