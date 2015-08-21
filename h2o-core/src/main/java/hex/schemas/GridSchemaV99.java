package hex.schemas;

import hex.grid.Grid;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelParametersSchema;
import water.api.Schema;

/**
 * REST endpoint representing single grid object.
 *
 * FIXME: Grid should contain also grid definition - model parameters
 * and definition of hyper parameters.
 */
public class GridSchemaV99 extends Schema<Grid, GridSchemaV99> {
  //
  // Inputs
  //
  @API(help = "Grid id")
  public KeyV3.GridKeyV3 grid_id;

  //
  // Outputs
  //
  @API(help = "Model IDs build by a grid search")
  public KeyV3.ModelKeyV3[] model_ids;

  @API(help = "Used hyper parameters.", direction = API.Direction.OUTPUT)
  public String[] hyper_names;

  @API(help = "List of failed parameters", direction = API.Direction.OUTPUT)
  public ModelParametersSchema[] failed_params; // Using common ancestor of XXXParamsV3

  @API(help = "List of detailed failure messages", direction = API.Direction.OUTPUT)
  public String[] failure_details;

  @API(help = "List of raw parameters causing model building failure", direction = API.Direction.OUTPUT)
  public String[][] failed_raw_params;

  @Override
  public Grid createImpl() {
    return Grid.GRID_PROTO;
  }
}
