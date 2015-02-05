package water.api;

import water.util.JStack;
import water.util.JStackCollectorTask.DStackTrace;

public class JStackV2 extends Schema<JStack, JStackV2> {
  @API(help="Stacktraces", direction=API.Direction.OUTPUT)
  public DStackTraceV2[] traces;

  public static class DStackTraceV2 extends Schema<DStackTrace, DStackTraceV2> {
    public DStackTraceV2() { }

    @API(help="Node name", direction=API.Direction.OUTPUT)
    public String node;

    @API(help="Unix epoch time", direction=API.Direction.OUTPUT)
    public long time;

    @API(help="One trace per thread", direction=API.Direction.OUTPUT)
    public String[] thread_traces;
  }
}
