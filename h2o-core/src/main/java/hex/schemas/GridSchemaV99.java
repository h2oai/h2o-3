package hex.schemas;

import hex.Model;
import hex.ModelMetrics;
import hex.grid.Grid;
import water.DKV;
import water.Key;
import water.api.*;
import water.api.schemas3.*;
import water.exceptions.H2OIllegalArgumentException;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * REST endpoint representing single grid object.
 *
 * FIXME: Grid should contain also grid definition - model parameters and definition of hyper parameters.
 */
public class GridSchemaV99 extends SchemaV3<Grid, GridSchemaV99> {
  //
  // Inputs
  //
  @API(help = "Grid id")
  public KeyV3.GridKeyV3 grid_id;

  @API(help = "Model performance metric to sort by. Examples: logloss, residual_deviance, mse, rmse, mae,rmsle, auc, r2, f1, recall, precision, accuracy, mcc, err, err_count, lift_top_group, max_per_class_error", required = false, direction = API.Direction.INOUT)
  public String sort_by;

  @API(help = "Specify whether sort order should be decreasing.", required = false, direction = API.Direction.INOUT)
  public boolean decreasing;

  //
  // Outputs
  //
  @API(help = "Model IDs built by a grid search")
  public KeyV3.ModelKeyV3[] model_ids;

  @API(help = "Used hyper parameters.", direction = API.Direction.OUTPUT)
  public String[] hyper_names;

  @API(help = "List of failed parameters", direction = API.Direction.OUTPUT)
  public ModelParametersSchemaV3[] failed_params; // Using common ancestor of XXXParamsV3

  @API(help = "List of detailed warning messages", direction = API.Direction.OUTPUT)
  public String[] warning_details;
  
  @API(help = "List of detailed failure messages", direction = API.Direction.OUTPUT)
  public String[] failure_details;

  @API(help = "List of detailed failure stack traces", direction = API.Direction.OUTPUT)
  public String[] failure_stack_traces;

  @API(help = "List of raw parameters causing model building failure", direction = API.Direction.OUTPUT)
  public String[][] failed_raw_params;

  @API(help = "Training model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBaseV3[] training_metrics;

  @API(help = "Validation model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBaseV3[] validation_metrics;

  @API(help = "Cross validation model metrics for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public ModelMetricsBaseV3[] cross_validation_metrics;

  @API(help = "Cross validation model metrics summary for the returned models; only returned if sort_by is set", direction = API.Direction.OUTPUT)
  public TwoDimTableV3[] cross_validation_metrics_summary;

  @API(help = "Directory for Grid automatic checkpointing", direction = API.Direction.OUTPUT)
  public String export_checkpoints_dir;

  @API(help="Summary", direction=API.Direction.OUTPUT)
  TwoDimTableV3 summary_table;

  @API(help="Scoring history", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  TwoDimTableV3 scoring_history;

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

    // Default sort order -- TODO: Outsource
    if (sort_by == null && modelKeys.size() > 0 && modelKeys.get(0) != null) {
      Model m = DKV.getGet(modelKeys.get(0));
      Model.GridSortBy sortBy = m != null ? m.getDefaultGridSortBy() : null;
      if (sortBy != null) {
        sort_by = sortBy._name;
        decreasing = sortBy._decreasing;
      }
    }

    // Check that we have a valid metric
    // If not, show all possible metrics
    if (modelKeys.size() > 0 && sort_by != null) {
      Set<String> possibleMetrics = ModelMetrics.getAllowedMetrics(modelKeys.get(0));
      if (!possibleMetrics.contains(sort_by.toLowerCase())) {
        throw new H2OIllegalArgumentException("Invalid argument for sort_by specified. Must be one of: " + Arrays.toString(possibleMetrics.toArray(new String[0])));
      }
    }

    // Are we sorting by model metrics?
    if (null != sort_by && ! sort_by.isEmpty()) {
      // sort the model keys
      modelKeys = ModelMetrics.sortModelsByMetric(sort_by, decreasing, modelKeys);

      // fill the metrics arrays
      training_metrics = new ModelMetricsBaseV3[modelKeys.size()];
      validation_metrics = new ModelMetricsBaseV3[modelKeys.size()];
      cross_validation_metrics = new ModelMetricsBaseV3[modelKeys.size()];
      cross_validation_metrics_summary = new TwoDimTableV3[modelKeys.size()];

      for (int i = 0; i < modelKeys.size(); i++) {
        Model m = DKV.getGet(modelKeys.get(i));

        if (m != null) {
          Model.Output o = m._output;

          if (null != o._training_metrics)
            training_metrics[i] = (ModelMetricsBaseV3) SchemaServer.schema(3, o._training_metrics).fillFromImpl(o
                ._training_metrics);
          if (null != o._validation_metrics) validation_metrics[i] = (ModelMetricsBaseV3) SchemaServer.schema(3, o
              ._validation_metrics).fillFromImpl(o._validation_metrics);
          if (null != o._cross_validation_metrics) cross_validation_metrics[i] = (ModelMetricsBaseV3) SchemaServer
              .schema(3, o._cross_validation_metrics).fillFromImpl(o._cross_validation_metrics);
          if (o._cross_validation_metrics_summary != null)
            cross_validation_metrics_summary[i] = new TwoDimTableV3(o._cross_validation_metrics_summary);
        }
      }
    }

    KeyV3.ModelKeyV3[] modelIds = new KeyV3.ModelKeyV3[modelKeys.size()];
    Key<Model>[] keys = new Key[modelKeys.size()];
    for (int i = 0; i < modelIds.length; i++) {
      modelIds[i] = new KeyV3.ModelKeyV3(modelKeys.get(i));
      keys[i] = modelIds[i].key();
    }
    grid_id = new KeyV3.GridKeyV3(grid._key);
    model_ids = modelIds;
    hyper_names = grid.getHyperNames();
    final Grid.SearchFailure failures = grid.getFailures();
    failed_params = toModelParametersSchema(failures.getFailedParameters());
    failure_details = failures.getFailureDetails();
    failure_stack_traces = failures.getFailureStackTraces();
    failed_raw_params = failures.getFailedRawParameters();
    warning_details = failures.getWarningDetails();
    export_checkpoints_dir = grid.getParams() != null ? grid.getParams()._export_checkpoints_dir : null;

    TwoDimTable t = grid.createSummaryTable(keys, sort_by, decreasing);
    if (t!=null)
      summary_table = new TwoDimTableV3().fillFromImpl(t);

    TwoDimTable h = grid.createScoringHistoryTable();
    if (h != null)
      scoring_history = new TwoDimTableV3().fillFromImpl(h);
    return this;
  }

  private ModelParametersSchemaV3[] toModelParametersSchema(Model.Parameters[] modelParameters) {
    if (modelParameters==null) return null;
    ModelParametersSchemaV3[] result = new ModelParametersSchemaV3[modelParameters.length];
    for (int i = 0; i < modelParameters.length; i++) {
      if (modelParameters[i] != null) {
        result[i] =
            (ModelParametersSchemaV3) SchemaServer.schema(SchemaServer.getLatestVersion(), modelParameters[i])
                .fillFromImpl(modelParameters[i]);
      } else {
        result[i] = null;
      }
    }
    return result;
  }
}
