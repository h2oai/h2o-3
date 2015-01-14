package water.api;

import water.util.JStack;
import water.util.JStackCollectorTask;

public class JStackV2 extends Schema<JStack, JStackV2> {
  @API(help="Stacktraces", direction=API.Direction.OUTPUT)
  public JStackCollectorTask.DStackTrace[] traces;
}
