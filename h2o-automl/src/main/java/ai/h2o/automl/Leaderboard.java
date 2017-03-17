package ai.h2o.automl;

import hex.*;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
   * Metric used to sort this leaderboard.
   */
  private String metric;

  /**
   * Metric direction used in the sort.
   */
  private boolean sort_decreasing;

  /**
   * UserFeedback object used to send, um, feedback to the, ah, user.  :-)
   * Note that multiple Leaderboards can potentially use the same UserFeedback
   * object.
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
    this.metric = metric;
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

    if (this.metric == null) {
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
          List<Key<Model>> newModelsSorted = ModelMetrics.sortModelsByMetric(metric, sort_decreasing, Arrays.asList(old.models));
          old.models = newModelsSorted.toArray(new Key[0]);
        }
        catch (H2OIllegalArgumentException e) {
          Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
          throw e;
        }

        // NOTE: we've now written over old.models
        // TODO: should take out of the tatomic
        if (oldLeader == null || ! oldLeader.equals(old.models[0]))
          newLeader[0] = old.models[0];

        return old;
      } // atomic
    }.invoke(this._key);

    // We've updated the DKV but not this instance, so:

    this.models = this.modelKeys();
    if (null != newLeader[0]) {
      userFeedback.info(UserFeedbackEvent.Stage.ModelTraining, "New leader: " + newLeader[0]);
      EckoClient.updateLeaderboard(this);
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


/*
  public static String toString(Model[] models) {
    return toString(null, models, " ", "\n");
  }

  public static String toString(String project, Model[] models) {
    return toString(project, models, "\n");
  }
  */
private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  public static String toString(String project, Model[] models, String fieldSeparator, String lineSeparator, boolean includeTitle) {
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
      ModelMetrics mm =
              m._output._cross_validation_metrics != null ?
                      m._output._cross_validation_metrics :
                      m._output._validation_metrics != null ?
                              m._output._validation_metrics :
                              m._output._training_metrics;

      if (! printedHeader) {
        sb.append("Model_ID");
        sb.append(fieldSeparator);

        if (m._output.isBinomialClassifier()) {
          sb.append("auc");
        } else if (m._output.isClassifier()) {
          sb.append("mean_per_class_error");
        } else if (m._output.isSupervised()) {
          sb.append("mean_residual_deviance");
        }
        sb.append(fieldSeparator);
        sb.append("timestamp");
        sb.append(lineSeparator);
        printedHeader = true;
      }

      sb.append(m._key.toString());
      sb.append(fieldSeparator);

      if (m._output.isBinomialClassifier()) {
        sb.append(((ModelMetricsBinomial)mm).auc());
      } else if (m._output.isClassifier()) {
        sb.append(((ModelMetricsMultinomial)mm).mean_per_class_error());
      } else if (m._output.isSupervised()) {
        sb.append(((ModelMetricsRegression)mm).residual_deviance());
      }
      sb.append(fieldSeparator);

      sb.append(timestampFormat.format(m._output._end_time));

      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  public String toString(String fieldSeparator, String lineSeparator) {
    return toString(project, models(), fieldSeparator, lineSeparator, true);
  }

  @Override
  public String toString() {
    return toString(" ; ", " | ");
  }
}
