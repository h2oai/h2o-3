package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import hex.*;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.util.*;
import java.util.stream.Stream;


/**
 * Utility to track all the models built for a given dataset type.
 * <p>
 * Note that if a new Leaderboard is made for the same project_name it'll
 * keep using the old model list, which allows us to run AutoML multiple
 * times and keep adding to the leaderboard.
 * <p>
 * The models are returned sorted by either an appropriate default metric
 * for the model category (auc, mean per class error, or mean residual deviance),
 * or by a metric that's set via #setMetricAndDirection.
 * <p>
 * TODO: make this robust against removal of models from the DKV.
 */
public class Leaderboard extends Lockable<Leaderboard> {
  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  private final String _project_name;

  /**
   * List of models for this leaderboard, sorted by metric so that the best is first,
   * according to the standard metric for the given model type.
   * <p>
   * Updated inside addModels().
   */
  private Key<Model>[] _model_keys = new Key[0];

  /**
   * Leaderboard/test set ModelMetrics objects for the models.
   * <p>
   * Updated inside addModels().
   */
  private final IcedHashMap<Key<ModelMetrics>, ModelMetrics> _leaderboard_model_metrics_cache = new IcedHashMap<>();

  /**
   * Map providing for a given metric name, the list of metric values in the same order as the models
   */
  private IcedHashMap<String, double[]> _metric_values = new IcedHashMap<>();

  /**
   * Metric used to sort this leaderboard.
   */
  private String _sort_metric;

  /**
   * Metrics reported in leaderboard
   * Regression metrics: deviance, rmse, mse, mae, rmsle
   * Binomial metrics: auc, logloss, aucpr, mean_per_class_error, rmse, mse
   * Multinomial metrics: logloss, mean_per_class_error, rmse, mse
   */
  private String[] _metrics;

  /**
   * The eventLog attached to same AutoML instance as this Leaderboard object.
   */
  private final Key<EventLog> _eventlog_key;

  /**
   * Frame for which we return the metrics, by default.
   */
  private final Key<Frame> _leaderboard_frame_key;

  /**
   * Checksum for the Frame for which we return the metrics, by default.
   */
  private final long _leaderboard_frame_checksum;

  /**
   * Constructs a new leaderboard (doesn't put it in DKV).
   * @param project_name
   * @param eventLog
   * @param leaderboardFrame
   * @param sort_metric
   */
  public Leaderboard(String project_name, EventLog eventLog, Frame leaderboardFrame, String sort_metric) {
    super(Key.make(idForProject(project_name)));
    _project_name = project_name;
    _eventlog_key = eventLog._key;
    _leaderboard_frame_key = leaderboardFrame == null ? null : leaderboardFrame._key;
    _leaderboard_frame_checksum = leaderboardFrame == null ? 0 : leaderboardFrame.checksum();
    _sort_metric = sort_metric == null ? null : sort_metric.toLowerCase();
  }

  /**
   * Retrieves a leaderboard from DKV or creates a fresh one and add it to DKV.
   *
   * Note that if the leaderboard is reused to add new models, we have to use the same leaderboard frame.
   *
   * IMPORTANT!
   * if the leaderboard is created without leaderboardFrame, the models will be sorted according to their default metrics
   * (in order of availability: cross-validation metrics, validation metrics, training metrics).
   * Therefore, if some models were trained with/without cross-validation, or with different training or validation frames,
   * then we can't guarantee the fairness of the leaderboard ranking.
   *
   * @param project_name
   * @param eventLog
   * @param leaderboardFrame
   * @param sort_metric
   * @return an existing leaderboard if there's already one in DKV for this project, or a new leaderboard added to DKV.
   */
  static Leaderboard getOrMake(String project_name, EventLog eventLog, Frame leaderboardFrame, String sort_metric) {
    Leaderboard leaderboard = DKV.getGet(Key.make(idForProject(project_name)));
    if (null != leaderboard) {
      if (leaderboardFrame != null
              && (!leaderboardFrame._key.equals(leaderboard._leaderboard_frame_key)
                  || leaderboardFrame.checksum() != leaderboard._leaderboard_frame_checksum)) {
        throw new H2OIllegalArgumentException("Cannot use leaderboard "+project_name+" with a new leaderboard frame"
                +" (existing leaderboard frame: "+leaderboard._leaderboard_frame_key+").");
      } else {
        eventLog.warn(Stage.Workflow, "New models will be added to existing leaderboard "+project_name
                +" (leaderboard frame="+leaderboard._leaderboard_frame_key+") with already "+leaderboard.getModelKeys().length+" models.");
      }
      if (sort_metric != null && !sort_metric.equals(leaderboard._sort_metric)) {
        leaderboard._sort_metric = sort_metric.toLowerCase();
        if (leaderboard.getLeader() != null) leaderboard.setDefaultMetrics(leaderboard.getLeader()); //reinitialize
      }
    } else {
      leaderboard = new Leaderboard(project_name, eventLog, leaderboardFrame, sort_metric);
    }
    DKV.put(leaderboard);
    return leaderboard;
  }

  /**
   * @param project_name
   * @return a Leaderboard id for the project name
   */
  public static String idForProject(String project_name) { return "AutoML_Leaderboard_" + project_name; }

  /**
   * @param metric
   * @return true iff the metric is a loss function
   */
  public static boolean isLossFunction(String metric) {
    return metric != null && !Arrays.asList("auc", "aucpr").contains(metric.toLowerCase());
  }

  public String getProject() {
    return _project_name;
  }

  /**
   * If no sort metric is provided when creating the leaderboard,
   * then a default sort metric will be automatically chosen based on the problem type:
   * <li>
   *     <ul>binomial classification: auc</ul>
   *     <ul>multinomial classification: logloss</ul>
   *     <ul>regression: deviance</ul>
   * </li>
   * @return the metric used to sort the models in the leaderboard.
   */
  public String getSortMetric() {
    return _sort_metric;
  }

  /**
   * The sort metric is always the first element in the list of metrics.
   *
   * @return the full list of metrics available in the leaderboard.
   */
  public String[] getMetrics() {
    return _metrics;
  }

  /**
   * Note: If no leaderboard was provided, then the models are sorted according to metrics obtained during training
   * in the following priority order depending on availability:
   * <li>
   *     <ol>cross-validation metrics</ol>
   *     <ol>validation metrics</ol>
   *     <ol>training metrics</ol>
   * </li>
   * @return the frame (if any) used to score the models in the leaderboard.
   */
  public Frame leaderboardFrame() {
    return _leaderboard_frame_key == null ? null : _leaderboard_frame_key.get();
  }

  /**
   * @return list of keys of models sorted by the default metric for the model category, fetched from the DKV
   */
  public Key<Model>[] getModelKeys() {
    return _model_keys;
  }

  /** Return the number of models in this Leaderboard. */
  int getModelCount() { return getModelKeys().length; }

  /**
   * @return list of models sorted by the default metric for the model category
   */
  public Model[] getModels() {
    Key<Model>[] modelKeys = getModelKeys();
    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];
    return getModelsFromKeys(modelKeys);
  }

  /**
   * @return list of models sorted by the given metric
   */
  public Model[] getModelsSortedByMetric(String metric) {
    Key<Model>[] modelKeys = sortModels(metric);
    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];
    return getModelsFromKeys(modelKeys);
  }

  /**
   * @return the model with the best sort metric value.
   * @see #getSortMetric()
   */
  public Model getLeader() {
    Key<Model>[] modelKeys = getModelKeys();
    if (modelKeys == null || 0 == modelKeys.length) return null;
    return modelKeys[0].get();
  }

  /**
   * @param modelKey
   * @return the rank for the given model key, according to the sort metric ranking (leader has rank 1).
   */
  public int getModelRank(Key<Model> modelKey) {
    return ArrayUtils.find(_model_keys, modelKey) + 1;
  }

  /**
   * @return the ordered values (asc or desc depending if sort metric is a loss function or not) for the sort metric.
   * @see #getSortMetric()
   * @see #isLossFunction(String)
   */
  public double[] getSortMetricValues() {
    return _sort_metric == null ? null : _metric_values.get(_sort_metric);
  }

  private EventLog eventLog() { return _eventlog_key.get(); }

  private void setDefaultMetrics(Model m) {
    String[] metrics = defaultMetricsForModel(m);
    if (_sort_metric == null) {
      _sort_metric = metrics.length > 0 ? metrics[0] : "mse"; // default to a metric "universally" available
    }
    // ensure metrics is ordered in such a way that sortMetric is the first metric, and without duplicates.
    int sortMetricIdx = ArrayUtils.find(metrics, _sort_metric);
    if (sortMetricIdx > 0) {
      metrics = ArrayUtils.remove(metrics, sortMetricIdx);
      metrics = ArrayUtils.prepend(metrics, _sort_metric);
    } else if (sortMetricIdx < 0){
      metrics = ArrayUtils.append(new String[]{_sort_metric}, metrics);
    }
    _metrics = metrics;
  }

  /**
   * Add the given models to the leaderboard.
   * Note that to make this easier to use from Grid, which returns its models in random order,
   * we allow the caller to add the same model multiple times and we eliminate the duplicates here.
   * @param newModels
   */
  final void addModels(final Key<Model>[] newModels) {
    if (null == _key)
      throw new H2OIllegalArgumentException("Can't add models to a Leaderboard which isn't in the DKV.");

    // This can happen if a grid or model build timed out:
    if (null == newModels || newModels.length == 0) {
      return;
    }

    final Key<Model>[] oldModelKeys = _model_keys;
    final Key<Model> oldLeaderKey = (oldModelKeys == null || 0 == oldModelKeys.length) ? null : oldModelKeys[0];

    // eliminate duplicates
    final Set<Key<Model>> uniques = new HashSet<>(oldModelKeys.length + newModels.length);
    uniques.addAll(Arrays.asList(oldModelKeys));
    uniques.addAll(Arrays.asList(newModels));
    final List<Key<Model>> newModelKeys = new ArrayList<>(uniques);

    Model model = null;
    final Frame leaderboardFrame = leaderboardFrame();
    final List<ModelMetrics> modelMetrics = new ArrayList<>();
    for (Key<Model> modelKey : newModelKeys) {
      model = modelKey.get();
      if (null == model) {
        eventLog().warn(Stage.ModelTraining, "Model in the leaderboard has unexpectedly been deleted from H2O: " + modelKey);
        continue;
      }

      // If leaderboardFrame is null, use default model metrics instead
      ModelMetrics mm;
      if (leaderboardFrame == null) {
        mm = ModelMetrics.defaultModelMetrics(model);
      } else {
        mm = ModelMetrics.getFromDKV(model, leaderboardFrame);
        if (mm == null) {
          //scores and magically stores the metrics where we're looking for it on the next line
          model.score(leaderboardFrame).delete();
          mm = ModelMetrics.getFromDKV(model, leaderboardFrame);
        }
      }
      modelMetrics.add(mm);
    }

    write_lock(); //no job/key needed as currently the leaderboard instance can only be updated by its corresponding AutoML job (otherwise, would need to pass a job param to addModels)
    if (_metrics == null) {
      // lazily set to default for this model category
      setDefaultMetrics(newModels[0].get());
    }

    for (ModelMetrics mm : modelMetrics) {
      if (mm != null) _leaderboard_model_metrics_cache.put(mm._key, mm);
    }
    // Sort by metric on the leaderboard/test set or default model metrics.
    final List<Key<Model>> sortedModelKeys;
    boolean sortDecreasing = !isLossFunction(_sort_metric);
    try {
      if (leaderboardFrame == null) {
        sortedModelKeys = ModelMetrics.sortModelsByMetric(_sort_metric, sortDecreasing, newModelKeys);
      } else {
        sortedModelKeys = ModelMetrics.sortModelsByMetric(leaderboardFrame, _sort_metric, sortDecreasing, newModelKeys);
      }
    } catch (H2OIllegalArgumentException e) {
      Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
      throw e;
    }

    final Key<Model>[] modelKeys = sortedModelKeys.toArray(new Key[0]);
    final Model[] models = getModelsFromKeys(modelKeys);
    // now, we can update leaderboard public state
    // (tried to narrow scope of write lock, but there are still private state mutations above: _leaderboard_set_metrics + all attributes set by setDefaultMetricAndDirection)
    for (String metric : _metrics) {
      _metric_values.put(metric, getMetrics(metric, models));
    }
    _model_keys = modelKeys;
    update();
    unlock();

    if (oldLeaderKey == null || !oldLeaderKey.equals(modelKeys[0])) {
      eventLog().info(Stage.ModelTraining,
              "New leader: "+modelKeys[0]+", "+ _sort_metric +": "+ _metric_values.get(_sort_metric)[0]);
    }
  } // addModels


  void addModel(final Key<Model> key) {
    if (null == key) return;

    Key<Model>keys[] = new Key[1];
    keys[0] = key;
    addModels(keys);
  }

  void addModel(final Model model) {
    if (null == model) return;

    Key<Model>keys[] = new Key[1];
    keys[0] = model._key;
    addModels(keys);
  }

  private static Model[] getModelsFromKeys(Key<Model>[] modelKeys) {
    Model[] models = new Model[modelKeys.length];
    int i = 0;
    for (Key<Model> modelKey : modelKeys)
      models[i++] = DKV.getGet(modelKey);
    return models;
  }

  /**
   * @return list of keys of models sorted by the given metric, fetched from the DKV
   */
  private Key<Model>[] sortModels(String metric) {
    Key<Model>[] models = getModelKeys();
    boolean decreasing = !isLossFunction(metric);
    List<Key<Model>> newModelsSorted =
            ModelMetrics.sortModelsByMetric(metric, decreasing, Arrays.asList(models));
    return newModelsSorted.toArray(new Key[0]);
  }

  private double[] getMetrics(String metric, Model[] models) {
    double[] metrics = new double[models.length];
    int i = 0;
    Frame leaderboardFrame = leaderboardFrame();
    for (Model m : models) {
      // If leaderboard frame exists, get metrics from there
      if (leaderboardFrame != null) {
        metrics[i++] = ModelMetrics.getMetricFromModelMetric(
            _leaderboard_model_metrics_cache.get(ModelMetrics.buildKey(m, leaderboardFrame)),
            metric
        );
      } else {
        // otherwise use default model metrics
        Key model_key = m._key;
        long model_checksum = m.checksum();
        ModelMetrics mm = ModelMetrics.defaultModelMetrics(m);
        metrics[i++] = ModelMetrics.getMetricFromModelMetric(
            _leaderboard_model_metrics_cache.get(ModelMetrics.buildKey(model_key, model_checksum, mm.frame()._key, mm.frame().checksum())),
            metric
        );
      }
    }
    return metrics;
  }

  /**
   * Delete object and its dependencies from DKV, including models.
   */
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    Log.debug("Cleaning up leaderboard from models "+Arrays.toString(_model_keys));
    if (cascade) {
      for (Key<Model> m : _model_keys) {
        Keyed.remove(m, fs, true);
      }
    }
    for (Key k : _leaderboard_model_metrics_cache.keySet())
      Keyed.remove(k, fs, true);
    return super.remove_impl(fs, cascade);
  }

  private static String[] defaultMetricsForModel(Model m) {
    if (m._output.isBinomialClassifier()) { //binomial
      return new String[] {"auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"};
    } else if (m._output.isMultinomialClassifier()) { // multinomial
      return new String[] {"mean_per_class_error", "logloss", "rmse", "mse"};
    } else if (m._output.isSupervised()) { // regression
      return new String[] {"deviance", "rmse", "mse", "mae", "rmsle"};
    }
    return new String[0];
  }

  private double[] getModelMetricValues(int rank) {
    assert rank >= 0 && rank < getModelKeys().length: "invalid rank";
    if (_metrics == null) return new double[0];
    final double[] values = new double[_metrics.length];
    for (int i=0; i < _metrics.length; i++) {
      values[i] = _metric_values.get(_metrics[i])[rank];
    }
    return values;
  }

  String rankTsv() {
    String lineSeparator = "\n";

    StringBuilder sb = new StringBuilder();
    sb.append("Error").append(lineSeparator);

    for (int i = getModelKeys().length - 1; i >= 0; i--) {
      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      sb.append(Arrays.toString(getModelMetricValues(i)));
      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  private static final String colMetricType = "double";
  private static final String colMetricFormat = "%.6f";

  private static final TwoDimTable makeTwoDimTable(String tableHeader, String[] metrics, int nrows) {
    assert metrics.length > 0;
    String sort_metric = metrics[0];
    assert sort_metric != null || nrows == 0 :
        "sort_metrics needs to be always not-null for non-empty array!";

    String description = nrows > 0 ? "models sorted in order of "+sort_metric+", best first"
                        : "no models in this leaderboard";
    String[] rowHeaders = new String[nrows];
    for (int i = 0; i < nrows; i++) rowHeaders[i] = ""+i;
    String[] colHeaders = ArrayUtils.append(new String[]{"model_id"}, metrics);
    String[] colTypes = ArrayUtils.copyAndFillOf(new String[]{"string"}, colHeaders.length, colMetricType);
    String[] colFormats = ArrayUtils.copyAndFillOf(new String[]{"%s"}, colHeaders.length, colMetricFormat);
    String colHeaderForRowHeader = nrows > 0 ? "#" : "-";

    return new TwoDimTable(
            tableHeader,
            description,
            rowHeaders,
            colHeaders,
            colTypes,
            colFormats,
            colHeaderForRowHeader
    );
  }

  private void addTwoDimTableRow(TwoDimTable table, int row, String modelID, String[] metrics) {
    int col = 0;
    table.set(row, col++, modelID);
    for (String metric : metrics) {
      double value = _metric_values.get(metric)[row];
      table.set(row, col++, value);
    }
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("Leaderboard for AutoML: " + _project_name, false);
  }

  TwoDimTable toTwoDimTable(String tableHeader, boolean leftJustifyModelIds) {
    String[] modelIDsFormatted = new String[_model_keys.length];
    String[] metrics = _metrics == null ? (_sort_metric == null ? new String[] {"unknown"} : new String[] {_sort_metric})
                      : _metrics;

    TwoDimTable table = makeTwoDimTable(tableHeader, metrics, _model_keys.length);

    int maxModelIdLen = Stream.of(_model_keys).mapToInt(k -> k.toString().length()).max().orElse(0);
    for (int i = 0; i < _model_keys.length; i++) {
      Key<Model> key = _model_keys[i];
      if (leftJustifyModelIds) {
        // %-s doesn't work in TwoDimTable.toString(), so fake it here:
        modelIDsFormatted[i] = (key.toString()+"                                                                                         ")
                .substring(0, maxModelIdLen);
      } else {
        modelIDsFormatted[i] = key.toString();
      }
      addTwoDimTableRow(table, i, modelIDsFormatted[i], metrics);
    }
    return table;
  }

  private String toString(String fieldSeparator, String lineSeparator, boolean includeTitle, boolean includeHeader) {
    final StringBuilder sb = new StringBuilder();
    if (includeTitle) {
      sb.append("Leaderboard for project_name \"")
              .append(_project_name)
              .append("\": ");

      if (_model_keys.length == 0) {
        sb.append("<empty>");
        return sb.toString();
      }
      sb.append(lineSeparator);
    }

    boolean printedHeader = false;
    for (int i = 0; i < _model_keys.length; i++) {
      final Key<Model> key = _model_keys[i];
      if (includeHeader && ! printedHeader) {
        sb.append("model_id");
        sb.append(fieldSeparator);
        String [] metrics = _metrics != null ? _metrics : new String[] {"unknown"};
        sb.append(Arrays.toString(metrics));
        sb.append(lineSeparator);
        printedHeader = true;
      }

      sb.append(key.toString());
      sb.append(fieldSeparator);
      double[] values = _metrics != null ? getModelMetricValues(i) : new double[] {Double.NaN};
      sb.append(Arrays.toString(values));
      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(" ; ", " | ", true, true);
  }

}
