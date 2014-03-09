package water;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import jsr166y.CountedCompleter;

class RPC<T> implements Delayed {
  public static final int RETRY_MS = 200;
  public int _tasknum;
  public H2ONode _target;
  public void response( AutoBuffer ab ) { throw H2O.unimpl(); }
  public static void tcp_ack( AutoBuffer ab ) { throw H2O.unimpl(); }
  public static void remote_exec( AutoBuffer ab ) { throw H2O.unimpl(); }
  public boolean isDone() { throw H2O.unimpl(); }
  public void call() { throw H2O.unimpl(); }
  public void cancel(boolean b) { throw H2O.unimpl(); }
  @Override public final long getDelay( TimeUnit unit ) { throw H2O.unimpl(); }
  @Override public final int compareTo( Delayed t ) { throw H2O.unimpl(); }

  public static class RPCCall extends H2O.H2OCountedCompleter implements Delayed {
    public int _tsknum;
    public long _started;
    public int _retry;
    public boolean _computed;
    public H2ONode _client;
    public volatile DTask _dt;
    static AtomicReferenceFieldUpdater<RPCCall,DTask> CAS_DT =
      AtomicReferenceFieldUpdater.newUpdater(RPCCall.class, DTask.class,"_dt");
    public RPCCall( DTask dt, H2ONode target, int tsknum ) { 
      if( dt != null ) throw H2O.unimpl(); 
    }
    void resend_ack() { throw H2O.unimpl(); }
    @Override public void compute2() { throw H2O.unimpl(); }
    @Override public void onCompletion( CountedCompleter caller ) { throw H2O.unimpl(); }
    @Override public boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) { throw H2O.unimpl();  }
    @Override public byte priority() { return _dt.priority(); }
    @Override public final long getDelay( TimeUnit unit ) { throw H2O.unimpl(); }
    @Override public final int compareTo( Delayed t ) {
      RPCCall r = (RPCCall)t;
      long nextTime = _started+_retry, rNextTime = r._started+r._retry;
      return nextTime == rNextTime ? 0 : (nextTime > rNextTime ? 1 : -1);
    }
  }
}
