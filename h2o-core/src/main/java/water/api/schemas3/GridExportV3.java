package water.api.schemas3;


import water.Iced;
import water.api.API;
import water.api.ModelExportAware;

public class GridExportV3 extends SchemaV3<Iced, GridExportV3> implements ModelExportAware {

  @API(help = "ID of the Grid to load from the directory",
      required = true, direction = API.Direction.INPUT, level = API.Level.critical)
  public String grid_id;

  @API(help = "Path to the directory with saved Grid search",
      required = true, direction = API.Direction.INPUT, level = API.Level.critical)
  public String grid_directory;
  
  @API(help = "True if objects referenced by params should also be saved.", 
      direction = API.Direction.INPUT)
  public boolean save_params_references = false;

  @API(direction = API.Direction.INPUT, help = "Flag indicating whether the exported model artifacts should also include CV Holdout Frame predictions",
          level = API.Level.secondary)
  public boolean export_cross_validation_predictions;

  @Override
  public boolean isExportCVPredictionsEnabled() {
    return export_cross_validation_predictions;
  }

}
