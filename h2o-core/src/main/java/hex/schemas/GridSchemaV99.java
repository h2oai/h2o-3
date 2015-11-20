package hex.schemas;

import hex.Model;
import hex.ModelMetrics;
import hex.grid.Grid;
import water.DKV;
import water.Key;
import water.api.*;

import java.util.ArrayList;
import java.util.List;

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

  @API(help = "Model performance metric to sort by.", required = false, direction = API.Direction.INOUT)
  public String sort_by;

  @API(help = "Sort order, \"desc\" or \"asc\".", required = false, direction = API.Direction.INOUT)
  public String sort_order;


  //
  // Outputs
  //
  @API(help = "Model IDs built by a grid search")
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

  @API(help = "Training model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBase[] training_metrics;

  @API(help = "Validation model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBase[] validation_metrics;

  @API(help = "Cross validation model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBase[] cross_validation_metrics;

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
    List<Key<Model>> modelKeys = new ArrayList<>(gridModelKeys.length); // pre-allocate
    for (Key k : gridModelKeys) {
      if (k != null && DKV.get(k) != null) {
        modelKeys.add(k);
      }
    }

    // Are we sorting by model metrics?
    if (null != sort_by && ! sort_by.isEmpty()) {
      // sort the model keys
      modelKeys = ModelMetrics.sortModelsByMetric(sort_by, sort_order, modelKeys);

      // fill the metrics arrays
      training_metrics = new ModelMetricsBase[modelKeys.size()];
      validation_metrics = new ModelMetricsBase[modelKeys.size()];
      cross_validation_metrics = new ModelMetricsBase[modelKeys.size()];

      for (int i = 0; i < modelKeys.size(); i++) {
        Model m = DKV.getGet(modelKeys.get(i));

        if (null != m) {
          Model.Output o = m._output;

          training_metrics[i] = (ModelMetricsBase) Schema.schema(3, o._training_metrics).fillFromImpl(o._training_metrics);
          if (null != o._validation_metrics) validation_metrics[i] = (ModelMetricsBase) Schema.schema(3, o._validation_metrics).fillFromImpl(o._validation_metrics);
          if (null != o._cross_validation_metrics) cross_validation_metrics[i] = (ModelMetricsBase) Schema.schema(3, o._cross_validation_metrics).fillFromImpl(o._cross_validation_metrics);
        }
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
