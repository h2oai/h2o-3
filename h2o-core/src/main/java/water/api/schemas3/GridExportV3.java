package water.api.schemas3;


import water.Iced;
import water.api.API;

public class GridExportV3 extends SchemaV3<Iced, GridExportV3> {

  @API(required = true, direction = API.Direction.INPUT, help = "Path to the directory with saved Grid search", level = API.Level.critical)
  public String grid_directory;
  
  @API(required = true, direction = API.Direction.INPUT, help = "ID of the Grid to load from the directory", level = API.Level.critical)
  public String grid_id;

}
