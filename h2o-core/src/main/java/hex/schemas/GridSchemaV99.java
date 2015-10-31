package hex.schemas;

import java.util.ArrayList;
import java.util.List;

import hex.Model;
import hex.grid.Grid;
import water.DKV;
import water.Key;
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

  @API(help = "List of detailed failure stack traces", direction = API.Direction.OUTPUT)
  public String[] failure_stack_traces;

  @API(help = "List of raw parameters causing model building failure", direction = API.Direction.OUTPUT)
  public String[][] failed_raw_params;

  @Override
  public Grid createImpl() {
    return Grid.GRID_PROTO;
  }

  @Override
  public GridSchemaV99 fillFromImpl(Grid grid) {
    Key<Model>[] gridModelKeys = grid.getModelKeys();
    // Return only keys which are referencing to existing objects in DKV
    // However, here is still implicit race, since we are sending
    // keys to client, but referenced models can be deleted in meantime
    // Hence, client has to be responsible for handling this situation
    // - call getModel and check for null model
    List<Key> modelKeys = new ArrayList<>(gridModelKeys.length); // pre-allocate
    for (Key k : gridModelKeys) {
      if (k != null && DKV.get(k) != null) {
        modelKeys.add(k);
      }
    }
    KeyV3.ModelKeyV3[] modelIds = new KeyV3.ModelKeyV3[modelKeys.size()];
    for (int i = 0; i < modelIds.length; i++) {
      modelIds[i] = new KeyV3.ModelKeyV3(modelKeys.get(i));
    }
    grid_id = new KeyV3.GridKeyV3(grid._key);
    model_ids = modelIds;
    hyper_names = grid.getHyperNames();
    failed_params = toModelParametersSchema(grid.getFailedParameters());
    failure_details = grid.getFailureDetails();
    failure_stack_traces = grid.getFailureStackTraces();
    failed_raw_params = grid.getFailedRawParameters();
    return this;
  }

  private ModelParametersSchema[] toModelParametersSchema(Model.Parameters[] modelParameters) {
    if (modelParameters==null) return null;
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
