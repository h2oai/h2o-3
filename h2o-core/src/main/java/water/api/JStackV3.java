package water.api;

import water.util.JStack;
import water.util.JStackCollectorTask.DStackTrace;

public class JStackV3 extends SchemaV3<JStack, JStackV3> {
  @API(help="Stacktraces", direction=API.Direction.OUTPUT)
  public DStackTraceV3[] traces;

  public static class DStackTraceV3 extends SchemaV3<DStackTrace, DStackTraceV3> {
    public DStackTraceV3() { }

    @API(help="Node name", direction=API.Direction.OUTPUT)
    public String node;

    @API(help="Unix epoch time", direction=API.Direction.OUTPUT)
    public long time;

    @API(help="One trace per thread", direction=API.Direction.OUTPUT)
    public String[] thread_traces;
  }
}
