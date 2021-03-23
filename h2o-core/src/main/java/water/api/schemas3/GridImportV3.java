package water.api.schemas3;


import water.Iced;
import water.api.API;

public class GridImportV3 extends SchemaV3<Iced, GridImportV3> {

  @API(help = "Full path to the file containing saved Grid",
      required = true, direction = API.Direction.INPUT, level = API.Level.critical)
  public String grid_path;

  @API(help = "If true will also load saved objects referenced by params. Will fail with an error if grid was saved without objects referenced by params.",
      direction = API.Direction.INPUT)
  public boolean load_params_references = false;

}
