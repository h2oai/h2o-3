package ai.h2o.automl;

import hex.*;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;
import water.util.TwoDimTable;

import java.text.SimpleDateFormat;
import java.util.*;

import static water.DKV.getGet;
import static water.Key.make;

/**
 * Utility to track all the models built for a given dataset type.
 * <p>
 * Note that if a new Leaderboard is made for the same project it'll
 * keep using the old model list, which allows us to run AutoML multiple
 * times and keep adding to the leaderboard.
 * <p>
 * The models are returned sorted by either an appropriate default metric
 * for the model category (auc, mean per class error, or mean residual deviance),
 * or by a metric that's set via #setMetricAndDirection.
 * <p>
 * TODO: make this robust against removal of models from the DKV.
 */
public class Leaderboard extends Keyed<Leaderboard> {
  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  private final String project;

  /**
   * List of models for this leaderboard, sorted by metric so that the best is first,
   * according to the standard metric for the given model type.  NOTE: callers should
   * access this through #models() to make sure they don't get a stale copy.
   */
  private Key<Model>[] models;

  /**
   * Sort metrics for the models in this leaderboard, in the same order as the models.
   */
  public double[] sort_metrics;

  /**
   * Metric used to sort this leaderboard.
   */
  private String sort_metric;

  /**
   * Metric direction used in the sort.
   */
  private boolean sort_decreasing;

  /**
   * UserFeedback object used to send, um, feedback to the, ah, user.  :-)
   * Right now this is a "new leader" message.
   */
  private UserFeedback userFeedback;

  /** HIDEME! */
  private Leaderboard() {
    throw new UnsupportedOperationException("Do not call the default constructor Leaderboard().");
  }

  /**
   *
   */
  public Leaderboard(String project, UserFeedback userFeedback) {
    this._key = make(idForProject(project));
    this.project = project;
    this.userFeedback = userFeedback;

    Leaderboard old = DKV.getGet(this._key);

    if (null == old) {
      this.models = new Key[0];
      DKV.put(this);
    }
  }

  // satisfy typing for job return type...
  public static class LeaderboardKeyV3 extends KeyV3<Iced, LeaderboardKeyV3, Leaderboard> {
    public LeaderboardKeyV3() {
    }

    public LeaderboardKeyV3(Key<Leaderboard> key) {
      super(key);
    }
  }

  public static String idForProject(String project) { return "AutoML_Leaderboard_" + project; }

  public String getProject() {
    return project;
  }

  public void setMetricAndDirection(String metric, boolean sortDecreasing){
    this.sort_metric = metric;
    this.sort_decreasing = sortDecreasing;
  }

  public void setDefaultMetricAndDirection(Model m) {
    if (m._output.isBinomialClassifier())
      setMetricAndDirection("auc", true);
    else if (m._output.isClassifier())
      setMetricAndDirection("mean_per_class_error", false);
    else if (m._output.isSupervised())
      setMetricAndDirection("mean_residual_deviance", false);
  }

  /**
   * Add the given models to the leaderboard.  Note that to make this easier to use from
   * Grid, which returns its models in random order, we allow the caller to add the same
   * model multiple times and we eliminate the duplicates here.
   * @param newModels
   */
  final public void addModels(final Key<Model>[] newModels) {
    if (null == this._key)
      throw new H2OIllegalArgumentException("Can't add models to a Leaderboard which isn't in the DKV.");

    if (this.sort_metric == null) {
      // lazily set to default for this model category
      setDefaultMetricAndDirection(newModels[0].get());
    }

    final Key<Model> newLeader[] = new Key[1]; // only set if there's a new leader

    new TAtomic<Leaderboard>() {
      @Override
      final public Leaderboard atomic(Leaderboard old) {
        if (old == null) old = new Leaderboard();

        final Key<Model>[] oldModels = old.models;
        final Key<Model> oldLeader = (oldModels == null || 0 == oldModels.length) ? null : oldModels[0];

        // eliminate duplicates
        Set<Key<Model>> uniques = new HashSet(oldModels.length + newModels.length);
        uniques.addAll(Arrays.asList(oldModels));
        uniques.addAll(Arrays.asList(newModels));
        old.models = uniques.toArray(new Key[0]);

        // Sort by metric.
        // TODO: If we want to train on different frames and then compare we need to score all the models and sort on the new metrics.
        try {
          List<Key<Model>> newModelsSorted = ModelMetrics.sortModelsByMetric(sort_metric, sort_decreasing, Arrays.asList(old.models));
          old.models = newModelsSorted.toArray(new Key[0]);
        }
        catch (H2OIllegalArgumentException e) {
          Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
          throw e;
        }

        Model[] models = new Model[old.models.length];
        old.sort_metrics = old.sortMetrics(modelsForModelKeys(old.models, models));

        // NOTE: we've now written over old.models
        // TODO: should take out of the tatomic
        if (oldLeader == null || ! oldLeader.equals(old.models[0]))
          newLeader[0] = old.models[0];

        return old;
      } // atomic
    }.invoke(this._key);

    // We've updated the DKV but not this instance, so:
    this.models = this.modelKeys();
    this.sort_metrics = sortMetrics(this.models());

    // always
    EckoClient.updateLeaderboard(this);
    if (null != newLeader[0]) {
      userFeedback.info(UserFeedbackEvent.Stage.ModelTraining, "New leader: " + newLeader[0]);
    }
  }


  public void addModel(final Key<Model> key) {
    Key<Model>keys[] = new Key[1];
    keys[0] = key;
    addModels(keys);
  }

  public void addModel(final Model model) {
    Key<Model>keys[] = new Key[1];
    keys[0] = model._key;
    addModels(keys);
  }

  private static Model[] modelsForModelKeys(Key<Model>[] modelKeys, Model[] models) {
    assert models.length >= modelKeys.length;
    int i = 0;
    for (Key<Model> modelKey : modelKeys)
      models[i++] = getGet(modelKey);
    return models;
  }

  /**
   * @return list of keys of models sorted by the default metric for the model category, fetched from the DKV
   */
  public Key<Model>[] modelKeys() {
    return ((Leaderboard)DKV.getGet(this._key)).models;
  }

  /**
   * @return list of keys of models sorted by the given metric , fetched from the DKV
   */
  public Key<Model>[] modelKeys(String metric, boolean sortDecreasing) {
    Key<Model>[] models = modelKeys();
    List<Key<Model>> newModelsSorted =
            ModelMetrics.sortModelsByMetric(metric, sortDecreasing, Arrays.asList(models));
    return newModelsSorted.toArray(new Key[0]);
  }

  /**
   * @return list of models sorted by the default metric for the model category
   */
  public Model[] models() {
    Key<Model>[] modelKeys = modelKeys();

    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];

    Model[] models = new Model[modelKeys.length];
    return modelsForModelKeys(modelKeys, models);
  }

  /**
   * @return list of models sorted by the given metric
   */
  public Model[] models(String metric, boolean sortDecreasing) {
    Key<Model>[] modelKeys = modelKeys(metric, sortDecreasing);

    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];

    Model[] models = new Model[modelKeys.length];
    return modelsForModelKeys(modelKeys, models);
  }

  public Model leader() {
    Key<Model>[] modelKeys = modelKeys();

    if (modelKeys == null || 0 == modelKeys.length) return null;

    return modelKeys[0].get();
  }

  public long[] timestamps(Model[] models) {
    long[] timestamps = new long[models.length];
    int i = 0;
    for (Model m : models)
      timestamps[i++] = m._output._end_time;
    return timestamps;
  }

  public double[] sortMetrics(Model[] models) {
    double[] sort_metrics = new double[models.length];
    int i = 0;
    for (Model m : models)
      sort_metrics[i++] = defaultMetricForModel(m);
    return sort_metrics;
  }

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  public void delete() {
    remove();
  }

  public void deleteWithChildren() {
    for (Model m : models())
      m.delete();
    delete();
  }

  public static double defaultMetricForModel(Model m) {
    ModelMetrics mm =
            m._output._cross_validation_metrics != null ?
                    m._output._cross_validation_metrics :
                    m._output._validation_metrics != null ?
                            m._output._validation_metrics :
                            m._output._training_metrics;

    if (m._output.isBinomialClassifier()) {
      return(((ModelMetricsBinomial)mm).auc());
    } else if (m._output.isClassifier()) {
      return(((ModelMetricsMultinomial)mm).mean_per_class_error());
    } else if (m._output.isSupervised()) {
      return(((ModelMetricsRegression)mm).residual_deviance());
    }
    Log.warn("Failed to find metric for model: " + m);
    return Double.NaN;
  }

  public static String defaultMetricNameForModel(Model m) {
    if (m._output.isBinomialClassifier()) {
      return "auc";
    } else if (m._output.isClassifier()) {
      return "mean per-class error";
    } else if (m._output.isSupervised()) {
      return "residual deviance";
    }
    return "unknown";
  }

  public String rankTsv() {
    String fieldSeparator = "\\t";
    String lineSeparator = "\\n";

    StringBuffer sb = new StringBuffer();
//    sb.append("Rank").append(fieldSeparator).append("Error").append(lineSeparator);
    sb.append("Error").append(lineSeparator);

    Model[] models = models();
    for (int i = models.length - 1; i >= 0; i--) {
      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      Model m = models[i];
      sb.append(defaultMetricForModel(m));
      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  public String timeTsv() {
    String fieldSeparator = "\\t";
    String lineSeparator = "\\n";

    StringBuffer sb = new StringBuffer();
    sb.append("Time").append(fieldSeparator).append("Error").append(lineSeparator);

    Model[] models = models();
    for (int i = models.length - 1; i >= 0; i--) {
      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      Model m = models[i];
      sb.append(timestampFormat.format(m._output._end_time));
      sb.append(fieldSeparator);

      sb.append(defaultMetricForModel(m));
      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  protected static final String[] colHeaders = {
          "model ID",
          "timestamp",
          "metric" };

  protected static final String[] colTypes= {
          "string",
          "string",
          "double" };

  protected static final String[] colFormats= {
          "%s",
          "%s",
          "%1.6d" };

  public static final TwoDimTable makeTwoDimTable(String tableHeader, int length) {
    String[] rowHeaders = new String[length];
    for (int i = 0; i < length; i++) rowHeaders[i] = "" + i;

    return new TwoDimTable(tableHeader,
            "models sorted in order of metric, best first",
            rowHeaders,
            Leaderboard.colHeaders,
            Leaderboard.colTypes,
            Leaderboard.colFormats,
            "#");
  }

  public void addTwoDimTableRow(TwoDimTable table, int row, String[] modelIDs, long[] timestamps, double[] errors) {
    int col = 0;
    table.set(row, col++, modelIDs[row]);
    table.set(row, col++, timestampFormat.format(new Date(timestamps[row])));
    table.set(row, col++, errors[row]);
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("Leaderboard for project: " + project, false);
  }

  public TwoDimTable toTwoDimTable(String tableHeader, boolean leftJustifyModelIds) {
    Model[] models = this.models();
    long[] timestamps = timestamps(models);
    String[] modelIDsFormatted = new String[models.length];
    
    TwoDimTable table = makeTwoDimTable(tableHeader, models.length);

    // %-s doesn't work in TwoDimTable.toString(), so fake it here:
    int maxModelIdLen = -1;
    for (Model m : models)
      maxModelIdLen = Math.max(maxModelIdLen, m._key.toString().length());
    for (int i = 0; i < models.length; i++)
      if (leftJustifyModelIds) {
        modelIDsFormatted[i] =
                (models[i]._key.toString() +
                        "                                                                                         ")
                        .substring(0, maxModelIdLen);
      } else {
        modelIDsFormatted[i] = models[i]._key.toString();
      }

    for (int i = 0; i < models.length; i++)
      addTwoDimTableRow(table, i, modelIDsFormatted, timestamps, sort_metrics);
    return table;
  }

  private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  public static String toString(String project, Model[] models, String fieldSeparator, String lineSeparator, boolean includeTitle, boolean includeHeader, boolean includeTimestamp) {
    StringBuilder sb = new StringBuilder();
    if (includeTitle) {
      sb.append("Leaderboard for project \"")
              .append(project)
              .append("\": ");

      if (models.length == 0) {
        sb.append("<empty>");
        return sb.toString();
      }
      sb.append(lineSeparator);
    }

    boolean printedHeader = false;
    for (Model m : models) {
      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      if (includeHeader && ! printedHeader) {
        sb.append("Model_ID");
        sb.append(fieldSeparator);

        sb.append(defaultMetricNameForModel(m));

        if (includeTimestamp) {
          sb.append(fieldSeparator);
          sb.append("timestamp");
        }
        sb.append(lineSeparator);
        printedHeader = true;
      }

      sb.append(m._key.toString());
      sb.append(fieldSeparator);

      sb.append(defaultMetricForModel(m));

      if (includeTimestamp) {
        sb.append(fieldSeparator);
        sb.append(timestampFormat.format(m._output._end_time));
      }

      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  public String toString(String fieldSeparator, String lineSeparator) {
    return toString(project, models(), fieldSeparator, lineSeparator, true, true, false);
  }

  @Override
  public String toString() {
    return toString(" ; ", " | ");
  }
}
