package water.util;

import java.util.Map;
import java.util.Map.Entry;
import water.*;

public class JStackCollectorTask extends MRTask<JStackCollectorTask> {
  public static class DStackTrace extends Iced {
    public final String _node;         // Node name
    public final long _time;           // Unix epoch time
    public final String[] _thread_traces;     // One per thread
    DStackTrace( String[] traces ) {
      _node = H2O.getIpPortString();
      _time = System.currentTimeMillis();
      _thread_traces = traces;
    }
  }
  public DStackTrace _traces[];        // One per Node

  @Override public void reduce(JStackCollectorTask that) {
    for( int i=0; i<_traces.length; ++i )
      if( _traces[i] == null )
        _traces[i] = that._traces[i];
  }

  @Override public void setupLocal() {
    _traces = new DStackTrace[H2O.CLOUD.size()];
    if( H2O.SELF._heartbeat._client ) return; // Clients are not in the cloud, and do not get stack traces
    Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
    String[] traces = new String[allStackTraces.size()];
    int i=0;
    for( Entry<Thread,StackTraceElement[]> el : allStackTraces.entrySet() ) {
      Thread t = el.getKey();
      SB sb = new SB().p('"').p(t.getName()).p('"');
      if (t.isDaemon()) sb.p(" daemon");
      sb.p(" prio=").p(t.getPriority());
      sb.p(" tid=").p(t.getId());
      sb.p(" java.lang.Thread.State: ").p(t.getState().toString());
      sb.nl();
      for( StackTraceElement aTrace : el.getValue()) sb.p("\tat ").p(aTrace.toString()).nl();
      String s = sb.toString();
      Log.info(s);
      traces[i++] = s;
    }
    _traces[H2O.SELF.index()] = new DStackTrace(traces);
  }

  @Override public byte priority() { return H2O.GUI_PRIORITY; }
}
