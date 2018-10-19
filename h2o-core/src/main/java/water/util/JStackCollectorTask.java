package water.util;

import water.H2O;
import water.Iced;
import water.MRTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class JStackCollectorTask extends MRTask<JStackCollectorTask> {
  JStackCollectorTask() { super(H2O.MIN_HI_PRIORITY); }
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

  private static class ThreadInfo {
    int _parked;
    int _active;
    int _blocked;
    int _unknown;

    public ThreadInfo add(ThreadInfo ti) {
      _parked += ti._parked;
      _active += ti._active;
      _blocked += ti._blocked;
      _unknown += ti._unknown;
      return this;
    }

    public double [] toDoubleArray(){
      return new double[]{_active + _unknown, _blocked, _parked, _active + _unknown + _blocked + _parked};
    }
    public boolean hasAny(){return _parked + _active + _blocked + _unknown > 0;}
  }

  enum ThreadType {HTTP_REQUEST, FJ, OTHER, TCP, JETTY, HADOOP}
  private static class ThreadKey implements Comparable<ThreadKey> {
    ThreadType _type;

    @Override
    public int compareTo(ThreadKey o) {
      return _type.ordinal() - o._type.ordinal();
    }
    @Override
    public String toString() {return _type.toString();}
  }

  // bruteforce search for H2O Servlet, don't call until other obvious cases were filtered out
  private int isH2OHTTPRequestThread(StackTraceElement [] elms){
    for(int i = 0; i < elms.length; ++i)
      if(elms[i].getClassName().equals("....JettyHTTPD$H2oDefaultServlet")) //TODO FIXME! No such class(H2oDefaultServlet) exists there now! Use class comparison if another one took the role.
        return i;
    return elms.length;
  }

  @Override public void setupLocal() {
    _traces = new DStackTrace[H2O.CLOUD.size()];
    if( H2O.SELF._heartbeat._client ) return; // Clients are not in the cloud, and do not get stack traces
    Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();

    // Known to be interesting
    ArrayList<String> http_traces = new ArrayList<>();
    http_traces.add("HttpReq traces");
    ArrayList<String> fj_traces = new ArrayList<>();
    fj_traces.add("FJ traces");
    // unknown - possibly interesting
    ArrayList<String> other_traces = new ArrayList<>();
    other_traces.add("'other' traces");
    // Most likely uninteresting
    ArrayList<String> tcp_traces = new ArrayList<>();
    tcp_traces.add("TCP traces");
    ArrayList<String> system_traces = new ArrayList<>();
    system_traces.add("system traces");
    ArrayList<String> jetty_traces = new ArrayList<>();
    jetty_traces.add("Jetty traces");

    ArrayList<String> h2o_sys_traces = new ArrayList<>();
    h2o_sys_traces.add("H2O System traces");

    Map<Integer,ThreadInfo> fjThreadSummary = new TreeMap<>();

    ThreadInfo threadSum = new ThreadInfo();
    ThreadInfo httpReqs = new ThreadInfo();
    ThreadInfo tcpThreads = new ThreadInfo();
    ThreadInfo otherThreads = new ThreadInfo();
    ThreadInfo jettythreads = new ThreadInfo();
    ThreadInfo h2oSysThreads = new ThreadInfo();
    ThreadInfo systemThreads = new ThreadInfo();

    for( Entry<Thread,StackTraceElement[]> el : allStackTraces.entrySet() ) {
      StackTraceElement [] elms = el.getValue();
      Thread t = el.getKey();
      int idx = elms.length;
      ArrayList<String> trace = null;
      ThreadInfo tinfo = null;
      if(elms.length == 0) continue;
      if(t.getName().startsWith("FJ-") && elms[elms.length-1].getClassName().contains("ForkJoinWorkerThread")) { // H2O specific FJ Thread
        trace = fj_traces;
        Integer fjq = Integer.parseInt(t.getName().substring(3, t.getName().indexOf('-', 3)));
        if (!fjThreadSummary.containsKey(fjq))
          fjThreadSummary.put(fjq, new ThreadInfo());
        tinfo = fjThreadSummary.get(fjq);
      } else if(elms[elms.length-1].getClassName().equals("water.TCPReceiverThread$TCPReaderThread")) {
        if (elms[elms.length - 2].getClassName().equals("water.AutoBuffer") && elms[elms.length - 2].getMethodName().equals("<init>")) {
          tcpThreads._parked++;
          continue;
        }
        trace = tcp_traces;
        tinfo = tcpThreads;
      } else if(elms[elms.length-1].getClassName().equals("water.MultiReceiverThread") || elms[elms.length-1].getClassName().equals("water.TCPReceiverThread") || elms[elms.length-1].getClassName().equals("water.HeartBeatThread")){
        trace = h2o_sys_traces;
        tinfo = h2oSysThreads;
      } else if(elms.length > 1 && elms[elms.length-2].getClassName().startsWith("java.util.concurrent.ThreadPoolExecutor") || elms[elms.length-1].getClassName().startsWith("java.lang.ref.Finalizer") || elms[elms.length-1].getClassName().startsWith("java.lang.ref.Reference")) {
        trace = system_traces;
        tinfo = systemThreads;
      }else if((idx = isH2OHTTPRequestThread(elms)) < elms.length) { // h2o HTTP request
        trace = http_traces;
        tinfo = httpReqs;
      } else if(elms.length > 1 && elms[elms.length-2].getClassName().startsWith("org.eclipse.jetty")){
        trace = jetty_traces;
        tinfo = jettythreads;
      } else {
        trace = other_traces;
        tinfo = otherThreads;
      }
      if(elms[0].getClassName().equals("sun.misc.Unsafe") && elms[0].getMethodName().equals("park")) {
        ++tinfo._parked;
        // don't include parked stacktraces
        continue;
      } if(t.getState().toString().equals("RUNNABLE")) {
        ++tinfo._active;
      } else if(t.getState().toString().contains("WAITING")) {
        ++tinfo._blocked;
      } else {
        ++tinfo._unknown;
        System.out.println("UNKNOWN STATE: " + t.getState());
      }
      SB sb = new SB().p('"').p(t.getName()).p('"');
      if (t.isDaemon()) sb.p(" daemon");
      sb.p(" prio=").p(t.getPriority());
      sb.p(" tid=").p(t.getId());
      sb.p(" java.lang.Thread.State: ").p(t.getState().toString());
      sb.nl();
      for( int j = 0; j < idx; ++j)
        sb.p("\tat ").p(elms[j].toString()).nl();
      trace.add(sb.toString());
    }
    // get the summary of idle threads
    // String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
    // String[] colFormats, String colHeaderForRowHeaders, String[][] strCellValues, double[][] dblCellValues
    ArrayList<String> rowNames = new ArrayList<>();
    ArrayList<double[]> cellVals = new ArrayList<>();

    if(httpReqs.hasAny()) {
      rowNames.add("HttpReq");
      cellVals.add(httpReqs.toDoubleArray());
    }
    for(Entry<Integer,ThreadInfo> e:fjThreadSummary.entrySet()) {
      rowNames.add("FJ-" + e.getKey());
      ThreadInfo fjt = e.getValue();
      threadSum.add(fjt);
      cellVals.add(fjt.toDoubleArray());
    }
    if(otherThreads.hasAny()) {
      rowNames.add("other");
      cellVals.add(otherThreads.toDoubleArray());
    }

    if(tcpThreads.hasAny()) {
      rowNames.add("TCP");
      cellVals.add(tcpThreads.toDoubleArray());
    }

    if(h2oSysThreads.hasAny()) {
      rowNames.add("h2osys");
      cellVals.add(h2oSysThreads.toDoubleArray());
    }
    if(systemThreads.hasAny()) {
      rowNames.add("system");
      cellVals.add(systemThreads.toDoubleArray());
    }

    if(jettythreads.hasAny()) {
      rowNames.add("jetty");
      cellVals.add(jettythreads.toDoubleArray());
    }

    rowNames.add("TOTAL");
    cellVals.add(threadSum.add(httpReqs).add(otherThreads).add(tcpThreads).add(systemThreads).add(jettythreads).toDoubleArray());


    TwoDimTable td = new TwoDimTable("Thread Summary", "Summary of running threads", rowNames.toArray(new String[0]), new String[] {"active","blocked","idle","TOTAL"}, new String[]{"int","int","int","int"}, new String[]{"%d","%d","%d","%d"}, "Thread",new String[cellVals.size()][],cellVals.toArray(new double[0][0]));

    // todo - sort FJ traces?

    String [] traces = new String[1+ http_traces.size() + fj_traces.size() + other_traces.size() + tcp_traces.size() + h2o_sys_traces.size() + system_traces.size() + jetty_traces.size()];
    int ii = 1;
    for(String t:http_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:fj_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:other_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:tcp_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:h2o_sys_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:system_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    for(String t:jetty_traces) {
      traces[ii++] = t;
      Log.info(t);
    }
    traces[0] = td.toString();
    Log.info(traces[0]);
    _traces[H2O.SELF.index()] = new DStackTrace(traces);
  }
}
