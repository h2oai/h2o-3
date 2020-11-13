package water.api.schemas3;


import water.Iced;
import water.api.API;

public class GridExportV3 extends SchemaV3<Iced, GridExportV3> {
  @API(help = "ID of the Grid to load from the directory",
      required = true, direction = API.Direction.INPUT, level = API.Level.critical)
  public String grid_id;

  @API(help = "Path to the directory with saved Grid search",
      required = true, direction = API.Direction.INPUT, level = API.Level.critical)
  public String grid_directory;
  
  @API(help = "True if objects referenced by params should also be saved.", 
      direction = API.Direction.INPUT)
  public boolean save_params_references = false;

}
