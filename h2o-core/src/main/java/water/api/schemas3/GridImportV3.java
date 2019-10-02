package water.api.schemas3;


import water.Iced;
import water.api.API;

public class GridImportV3 extends SchemaV3<Iced, GridImportV3> {

  @API(required = true, direction = API.Direction.INPUT, help = "Full path to the file containing saved Grid",
          level = API.Level.critical)
  public String grid_path;


}
