package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class AboutV3 extends SchemaV3<Iced, AboutV3> {

  @API(help = "List of properties about this running H2O instance", direction = API.Direction.OUTPUT)
  public AboutEntryV3 entries[];

}
