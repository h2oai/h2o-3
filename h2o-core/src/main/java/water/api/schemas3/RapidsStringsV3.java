package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsStringsV3 extends RapidsSchemaV3<Iced,RapidsStringsV3> {

  @API(help="String array result", direction=API.Direction.OUTPUT)
  public String[] string;

  public RapidsStringsV3() {}

  public RapidsStringsV3(String[] ss) { string = ss; }
}
