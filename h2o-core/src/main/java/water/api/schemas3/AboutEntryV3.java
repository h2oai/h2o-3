package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.AboutHandler;

/**
 */
public class AboutEntryV3 extends SchemaV3<Iced, AboutEntryV3> {

  @API(help = "Property name", direction = API.Direction.OUTPUT)
  public String name;

  @API(help = "Property value", direction = API.Direction.OUTPUT)
  public String value;


  public AboutEntryV3() {}
  public AboutEntryV3(String n, String v) {
    name = n;
    value = v;
  }
}
