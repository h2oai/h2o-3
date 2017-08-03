package water.api.schemas3;


import water.Iced;
import water.api.API;

public class CapabilitiesV3 extends RequestSchemaV3<Iced, CapabilitiesV3> {

  @API(help = "List of H2O capabilities", direction = API.Direction.OUTPUT)
  public CapabilityEntryV3[] capabilities;
}
