package water.util;

import java.util.Map;
import java.util.Map.Entry;
import water.MRTask;
import water.H2O;

public class JStackCollectorTask extends MRTask<JStackCollectorTask> {
  public String[] _traces; // for each node in the cloud it contains all threads stack traces

  @Override public void reduce(JStackCollectorTask that) {
    for( int i=0; i<_traces.length; ++i )
      if( _traces[i] == null )
        _traces[i] = that._traces[i];
  }

  @Override public void setupLocal() {
    _traces = new String[H2O.CLOUD.size()];
    Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
    StringBuilder sb = new StringBuilder();
    for( Entry<Thread,StackTraceElement[]> el : allStackTraces.entrySet() ) {
      append(sb, el.getKey());
      append(sb, el.getValue());
      sb.append('\n');
    }
    _traces[H2O.SELF.index()] = sb.toString();
  }

  @Override public byte priority() { return H2O.GUI_PRIORITY; }

  private void append(final StringBuilder sb, final Thread t) {
    sb.append('"').append(t.getName()).append('"');
    if (t.isDaemon()) sb.append(" daemon");
    sb.append(" prio=").append(t.getPriority());
    sb.append(" tid=").append(t.getId());
    sb.append(" java.lang.Thread.State: ").append(t.getState());
    sb.append('\n');
  }

  private void append(final StringBuilder sb, final StackTraceElement[] trace) {
    for (StackTraceElement aTrace : trace) sb.append("\tat ").append(aTrace).append('\n');
  }
}
