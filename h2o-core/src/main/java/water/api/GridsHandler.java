package water.api;

import hex.Model;
import hex.grid.Grid;
import hex.schemas.GridSchemaV99;
import water.H2O;
import water.Key;

/**
 * /Grids/ end-point handler.
 */
public class GridsHandler extends Handler {

  /**
   * Return all the grids.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GridSchemaV99 list(int version, GridSchemaV99 s) {
    throw H2O.unimpl();
  }

  /**
   * Return a specified grid.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GridSchemaV99 fetch(int version, GridSchemaV99 s) {
    Grid grid = getFromDKV("grid_id", s.grid_id.key(), Grid.class);
    Key<Model>[] models = grid.getModelKeys();
    KeyV3.ModelKeyV3[] modelIds = new KeyV3.ModelKeyV3[models.length];
    for (int i = 0; i < modelIds.length; i++) {
      modelIds[i] = new KeyV3.ModelKeyV3(models[i]);
    }
    s.model_ids = modelIds;
    s.hyper_names = grid.getHyperNames();
    s.failed_params = toModelParametersSchema(grid.getFailedParameters());
    s.failure_details = grid.getFailureDetails();
    s.failed_raw_params = grid.getFailedRawParameters();
    return s;
  }

  private ModelParametersSchema[] toModelParametersSchema(Model.Parameters[] modelParameters) {
    ModelParametersSchema[] result = new ModelParametersSchema[modelParameters.length];
    for (int i = 0; i < modelParameters.length; i++) {
      if (modelParameters[i] != null) {
        result[i] =
            (ModelParametersSchema) Schema.schema(Schema.getLatestVersion(), modelParameters[i])
                .fillFromImpl(modelParameters[i]);
      } else {
        result[i] = null;
      }
    }
    return result;
  }
}
