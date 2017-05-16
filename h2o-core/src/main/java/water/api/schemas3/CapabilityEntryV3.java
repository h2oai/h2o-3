package water.api.schemas3;

import water.Iced;
import water.api.API;

public class CapabilityEntryV3 extends SchemaV3<Iced, CapabilityEntryV3> {
  @API(help = "Extension name", direction = API.Direction.OUTPUT)
  public String name;

  public CapabilityEntryV3() {}
  public CapabilityEntryV3(String name) {
    this.name = name;
  }
}
