package hex.schemas;

import hex.grid.Grid;
import water.api.API;
import water.api.KeyV3;
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

  @Override
  public Grid createImpl() {
    return new Grid(null, null, null, null);
  }
}
