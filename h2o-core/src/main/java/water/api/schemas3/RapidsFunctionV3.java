package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsFunctionV3 extends RapidsSchemaV3<Iced,RapidsFunctionV3> {

  @API(help="Function result", direction=API.Direction.OUTPUT)
  public String funstr;

  public RapidsFunctionV3() {}

  public RapidsFunctionV3(String s) { funstr = s; }
}
