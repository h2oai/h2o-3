package water.automl.api.schemas3;

import water.api.API;
import water.api.schemas3.RequestSchemaV3;
import water.automl.api.AutoMLHandler;

public class AutoMLsV99 extends RequestSchemaV3<AutoMLHandler.AutoMLs, AutoMLsV99> {

  // Input fields
  @API(help="Name of project of interest", json=false)
  public String project_name;

  // Output fields
  @API(help="AutoMLs", direction=API.Direction.OUTPUT)
  public AutoMLV99[] auto_ml_runs;

}
