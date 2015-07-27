package water;
import water.util.Log;

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
  static DelayQueue<RPC> PENDING = new DelayQueue<>();

  // The Run Method.

  // Started by main() on a single thread, handle timing-out UDP packets
  public void run() {
    Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    while( true ) {
      try {
        RPC t = PENDING.take();
        // One-shot timeout effect.  Retries need to re-insert back in the queue
        if( H2O.CLOUD.contains(t._target) ||
            // Also retry clients who do not appear to be shutdown
            (t._target._heartbeat._client && t._retry <  HeartBeatThread.CLIENT_TIMEOUT) ) {
          if( !t.isDone() && !t._nack ) {
            if(++t._resendsCnt % 10 == 0)
              Log.warn("Got " + t._resendsCnt + " resends on task #" + t._tasknum + ", class = " + t._dt.getClass().getSimpleName());
            t.call();
          }
        } else {                // Target is dead, nobody to retry to
          t.cancel(true);
        }
      } catch( InterruptedException e ) {
        // Interrupted while waiting for a packet?
        // Blow it off and go wait again...
      }
    }
  }
}
