package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsStringV3 extends RapidsSchemaV3<Iced,RapidsStringV3> {

  @API(help="String result", direction=API.Direction.OUTPUT)
  public String string;

  public RapidsStringV3() {}
  public RapidsStringV3(String s) { string = s; }
}
