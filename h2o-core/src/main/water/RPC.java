package water;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import jsr166y.CountedCompleter;

class RPC<T> implements Delayed {
  static final int RETRY_MS = 200;
  int _tasknum;
  H2ONode _target;
  void response( AutoBuffer ab ) { throw H2O.unimpl(); }
  static void tcp_ack( AutoBuffer ab ) { throw H2O.unimpl(); }
  static void remote_exec( AutoBuffer ab ) { throw H2O.unimpl(); }
  boolean isDone() { throw H2O.unimpl(); }
  void call() { throw H2O.unimpl(); }
  void cancel(boolean b) { throw H2O.unimpl(); }
  @Override public final long getDelay( TimeUnit unit ) { throw H2O.unimpl(); }
  @Override public final int compareTo( Delayed t ) { throw H2O.unimpl(); }

  static class RPCCall extends H2O.H2OCountedCompleter implements Delayed {
    int _tsknum;
    long _started;
    int _retry;
    boolean _computed;
    H2ONode _client;
    volatile DTask _dt;
    static AtomicReferenceFieldUpdater<RPCCall,DTask> CAS_DT =
      AtomicReferenceFieldUpdater.newUpdater(RPCCall.class, DTask.class,"_dt");
    RPCCall( DTask dt, H2ONode target, int tsknum ) { 
      if( dt != null ) throw H2O.unimpl(); 
    }
    void resend_ack() { throw H2O.unimpl(); }
    @Override void compute2() { throw H2O.unimpl(); }
    @Override public void onCompletion( CountedCompleter caller ) { throw H2O.unimpl(); }
    @Override public boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) { throw H2O.unimpl();  }
    @Override byte priority() { return _dt.priority(); }
    @Override public final long getDelay( TimeUnit unit ) { throw H2O.unimpl(); }
    @Override public final int compareTo( Delayed t ) {
      RPCCall r = (RPCCall)t;
      long nextTime = _started+_retry, rNextTime = r._started+r._retry;
      return nextTime == rNextTime ? 0 : (nextTime > rNextTime ? 1 : -1);
    }
  }
}
