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
 * TODO: make this robust against removal of models from the DKV.
 */
public class Leaderboard extends Iced {
  /** Identifier for the models that should be groupled together in the leaderboard (e.g., "airlines" and "iris"). */
  private final String project;

  /** Key to the list of models which are stored in the DKV. */
  private final Key<ModelList> modelListKey;

  private Leaderboard() {
    throw new NotImplementedException();
  }

  public Leaderboard(String project) {
    this.project = project;

    // Note that if a new Leaderboard is made for the same project it'll keep using the old ModelList.
    this.modelListKey = make("AutoML_Leaderboard_" + project, (byte) 0, (byte) 2 /*builtin key*/, false);  // public for the test
  }

  public String getProject() {
    return project;
  }

  public ModelList modelList() {
    return getGet(modelListKey);
  }

  /**
   * List of models, sorted by metric so that the best is on top, according to the standard metric for the given model type.
   */
  private class ModelList extends Keyed {
    Key<Model>[] _models;

    ModelList() {
      super(modelListKey);
      _models = new Key[0];
    }

    @Override
    protected long checksum_impl() {
      throw H2O.fail("no such method for ModelList");
    }
  }

  public void addModels(final Key<Model>[] newModels) {
    new TAtomic<ModelList>() {
      @Override
      public ModelList atomic(ModelList old) {
        if (old == null) old = new ModelList();

        Key<Model>[] oldModels = old._models;
        old._models = new Key[oldModels.length + newModels.length];
        System.arraycopy(oldModels, 0, old._models, 0, oldModels.length);
        System.arraycopy(newModels, 0, old._models, oldModels.length, newModels.length);

        Model m = DKV.getGet(old._models[0]);

        // Sort by metric.
        // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
        // If we want to train on different frames and then compare we need to score all the models and sort on the new metrics.
        List<Key<Model>> newModelsSorted = null;
        try {
          if (m._output.isBinomialClassifier())
            newModelsSorted = ModelMetrics.sortModelsByMetric("auc", true, Arrays.asList(old._models));
          else if (m._output.isClassifier())
            newModelsSorted = ModelMetrics.sortModelsByMetric("mean_per_class_error", false, Arrays.asList(old._models));
          else if (m._output.isSupervised())
            newModelsSorted = ModelMetrics.sortModelsByMetric("mean_residual_deviance", false, Arrays.asList(old._models));
        }
        catch (H2OIllegalArgumentException e) {
          Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
          throw e;
        }
        old._models = newModelsSorted.toArray(new Key[0]);
        return old;
      }
    }.invoke(modelListKey);
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

  public Model[] models() {
    ModelList ml = getGet(modelListKey);
    if (ml == null) return new Model[0];

    Model[] models = new Model[ml._models.length];
    return modelsForModelKeys(ml._models, models);
  }

  public Model leader() {
    ModelList ml = getGet(modelListKey);

    if (null == ml) return null;
    return getGet(ml._models[0]);
  }

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  public void delete() {
    DKV.remove(modelListKey);
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
