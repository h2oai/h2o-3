package ai.h2o.automl;

import hex.Model;
import hex.ModelMetrics;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;

import java.util.Arrays;
import java.util.List;

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
  private boolean sortDecreasing;

  /** HIDEME! */
  private Leaderboard() {
    throw new NotImplementedException();
  }

  /**
   *
   */
  public Leaderboard(String project) {
    this._key = make("AutoML_Leaderboard_" + project, (byte) 0, (byte) 2 /*builtin key*/, false);
    this.project = project;

    Leaderboard old = DKV.getGet(this._key);

    if (null == old) {
      this.models = new Key[0];
      DKV.put(this);
    }
  }

  public String getProject() {
    return project;
  }

  public void setMetricAndDirection(String metric, boolean sortDecreasing){
    this.metric = metric;
    this.sortDecreasing = sortDecreasing;
  }

  public void setDefaultMetricAndDirection(Model m) {
    if (m._output.isBinomialClassifier())
      setMetricAndDirection("auc", true);
    else if (m._output.isClassifier())
      setMetricAndDirection("mean_per_class_error", false);
    else if (m._output.isSupervised())
      setMetricAndDirection("mean_residual_deviance", false);
  }

  public void addModels(final Key<Model>[] newModels) {
    if (null == this._key)
      throw new H2OIllegalArgumentException("Can't add models to a Leaderboard which isn't in the DKV.");

    if (this.metric == null) {
      // lazily set to default for this model category
      setDefaultMetricAndDirection(newModels[0].get());
    }

    new TAtomic<Leaderboard>() {
      @Override
      public Leaderboard atomic(Leaderboard old) {
        if (old == null) old = new Leaderboard();

        Key<Model>[] oldModels = old.models;
        old.models = new Key[oldModels.length + newModels.length];
        System.arraycopy(oldModels, 0, old.models, 0, oldModels.length);
        System.arraycopy(newModels, 0, old.models, oldModels.length, newModels.length);

        // Sort by metric.
        // TODO: If we want to train on different frames and then compare we need to score all the models and sort on the new metrics.
        try {
          List<Key<Model>> newModelsSorted = ModelMetrics.sortModelsByMetric(metric, sortDecreasing, Arrays.asList(old.models));
          old.models = newModelsSorted.toArray(new Key[0]);
        }
        catch (H2OIllegalArgumentException e) {
          Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
          throw e;
        }
        return old;
      } // atomic
    }.invoke(this._key);
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


  public static String toString(Model[] models) {
    return toString(null, models, "\n");
  }

  public static String toString(String project, Model[] models) {
    return toString(project, models, "\n");
  }

  public static String toString(String project, Model[] models, String separator) {
    StringBuilder sb = new StringBuilder("Leaderboard for project \"" + project + "\": ");

    if (models.length == 0) {
      sb.append("<empty>");
      return sb.toString();
    }
    sb.append(separator);

    for (Model m : models) {
      sb.append(m._key.toString());
      sb.append(" ");

      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      // TODO: if validation metrics are available, print those.
      if (m._output.isBinomialClassifier()) {
        sb.append("auc: ");
        sb.append(m.auc());
      } else if (m._output.isClassifier()) {
        sb.append("mean per class error: ");
        sb.append(m.mean_per_class_error());
      } else if (m._output.isSupervised()) {
        sb.append("mean residual deviance: ");
        sb.append(m.deviance());
      }

      sb.append(separator);
    }
    return sb.toString();
  }

  public String toString(String separator) {
    return toString(project, models(), separator);
  }

  @Override
  public String toString() {
    return toString(" | ");
  }
}
