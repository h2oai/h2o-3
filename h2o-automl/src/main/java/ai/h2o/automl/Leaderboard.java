package ai.h2o.automl;

import hex.*;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static water.DKV.getGet;
import static water.Key.make;


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
public class Leaderboard extends Keyed<Leaderboard> {
  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  private final String project_name;

  /**
   * List of models for this leaderboard, sorted by metric so that the best is first,
   * according to the standard metric for the given model type.
   * <p>
   * Updated inside addModels().
   */
  private Key<Model>[] models = new Key[0];

  /**
   * Leaderboard/test set ModelMetrics objects for the models.
   * <p>
   * Updated inside addModels().
   */
  private IcedHashMap<Key<ModelMetrics>, ModelMetrics> leaderboard_set_metrics = new IcedHashMap<>();

  /**
   * Sort metrics for the models in this leaderboard, in the same order as the models.
   * <p>
   * Updated inside addModels().
   */
  public double[] sort_metrics = new double[0];

  /**
   * Additional metrics for the models in this leaderboard, in the same order as the models
   * Regression metrics: rmse, mse, mae, and rmsle
   * Binomial metrics: logloss, mean_per_class_error, rmse, & mse
   * Multinomial metrics: logloss, mean_per_class_error, rmse, & mse
   * <p>
   * Updated inside addModels().
   */
  public double[] mean_residual_deviance = new double[0];
  public double[] rmse = new double[0];
  public double[] mse = new double[0];
  public double[] mae = new double[0];
  public double[] rmsle = new double[0];
  public double[] logloss = new double[0];
  public double[] auc = new double[0];
  public double[] mean_per_class_error = new double[0];

  /**
   * Metric used to sort this leaderboard.
   */
  String sort_metric;

  /**
   * Other metrics reported in leaderboard
   * Regression metrics: rmse, mse, mae, and rmsle
   * Binomial metrics: logloss, mean_per_class_error, rmse, & mse
   * Multinomial metrics: logloss, mean_per_class_error, rmse, & mse
   */
  private String[] other_metrics;

  /**
   * Metric direction used in the sort.
   */
  private boolean sort_decreasing;

  /**
   * Have we set the sort_metric based on a model in the leadboard?
   */
  private boolean have_set_sort_metric = false;

  /**
   * UserFeedback object used to send, um, feedback to the, ah, user.  :-)
   * Right now this is a "new leader" message.
   */
  private UserFeedback userFeedback;

  /**
   * Frame for which we return the metrics, by default.
   */
  private Frame leaderboardFrame;

  /**
   * Checksum for the Frame for which we return the metrics, by default.
   */
  private long leaderboardFrameChecksum;

  /**
   *
   */
  public Leaderboard(String project_name, UserFeedback userFeedback, Frame leaderboardFrame, String sort_metric) {
    this._key = make(idForProject(project_name));
    this.project_name = project_name;
    this.userFeedback = userFeedback;
    this.leaderboardFrame = leaderboardFrame;
    this.leaderboardFrameChecksum = leaderboardFrame == null ? 0 : leaderboardFrame.checksum();
    this.sort_metric = sort_metric == null ? null : sort_metric.toLowerCase();
  }

  static Leaderboard getOrMakeLeaderboard(String project_name, UserFeedback userFeedback, Frame leaderboardFrame, String sort_metric) {
    Leaderboard exists = DKV.getGet(Key.make(idForProject(project_name)));
    if (null != exists) {
      exists.userFeedback = userFeedback;
      exists.leaderboardFrame = leaderboardFrame;
      if (sort_metric != null) {
        exists.sort_metric = sort_metric.toLowerCase();
        exists.sort_decreasing = exists.sort_metric.equals("auc");
      }
      exists.leaderboardFrameChecksum = leaderboardFrame == null ? 0 : leaderboardFrame.checksum();

      DKV.put(exists);
      return exists;
    }

    Leaderboard newLeaderboard = new Leaderboard(project_name, userFeedback, leaderboardFrame, sort_metric);
    DKV.put(newLeaderboard);
    return newLeaderboard;
  }

  // satisfy typing for job return type...
  public static class LeaderboardKeyV3 extends KeyV3<Iced, LeaderboardKeyV3, Leaderboard> {
    public LeaderboardKeyV3() {
    }

    public LeaderboardKeyV3(Key<Leaderboard> key) {
      super(key);
    }
  }

  public static String idForProject(String project_name) { return "AutoML_Leaderboard_" + project_name; }

  public String getProject() {
    return project_name;
  }

  private void setMetricAndDirection(String metric, String[] otherMetrics, boolean sortDecreasing) {
    this.sort_metric = metric;
    this.other_metrics = otherMetrics;
    this.sort_decreasing = sortDecreasing;
    this.have_set_sort_metric = true;
    DKV.put(this);
  }

  private void setDefaultMetricAndDirection(Model m) {
    String[] metrics;
    if (m._output.isBinomialClassifier()) { //Binomial
      metrics = new String[]{"logloss", "mean_per_class_error", "rmse", "mse"};
      if(this.sort_metric == null) {
        this.sort_metric = "auc";
      }
    }
    else if (m._output.isMultinomialClassifier()) { //Multinomial
      metrics = new String[]{"logloss", "rmse", "mse"};
      if(this.sort_metric == null) {
        this.sort_metric = "mean_per_class_error";
      }
    }
    else { //Regression
      metrics = new String[]{"rmse", "mse", "mae", "rmsle"};
      if(this.sort_metric == null) {
        this.sort_metric = "mean_residual_deviance";
      }
    }
    boolean sortDecreasing = this.sort_metric.equals("auc");
    setMetricAndDirection(this.sort_metric, metrics, sortDecreasing);
  }

  /**
   * Add the given models to the leaderboard.  Note that to make this easier to use from
   * Grid, which returns its models in random order, we allow the caller to add the same
   * model multiple times and we eliminate the duplicates here.
   * @param newModels
   */
  final void addModels(final Key<Model>[] newModels) {
    if (null == this._key)
      throw new H2OIllegalArgumentException("Can't add models to a Leaderboard which isn't in the DKV.");

    // This can happen if a grid or model build timed out:
    if (null == newModels || newModels.length == 0) {
      return;
    }

    if (! this.have_set_sort_metric) {
      // lazily set to default for this model category
      setDefaultMetricAndDirection(newModels[0].get());
    }

    final Key<Model> newLeader[] = new Key[1]; // only set if there's a new leader
    final double newLeaderSortMetric[] = new double[1];

    new TAtomic<Leaderboard>() {
      @Override
      final public Leaderboard atomic(Leaderboard updating) {
        if (updating == null) {
          Log.err("trying to update null leaderboard!");
          throw new H2OIllegalArgumentException("Trying to update a null leaderboard.");
        }

        final Key<Model>[] oldModels = updating.models;
        final Key<Model> oldLeader = (oldModels == null || 0 == oldModels.length) ? null : oldModels[0];

        // eliminate duplicates
        Set<Key<Model>> uniques = new HashSet(oldModels.length + newModels.length);
        uniques.addAll(Arrays.asList(oldModels));
        uniques.addAll(Arrays.asList(newModels));
        updating.models = uniques.toArray(new Key[0]);

        // Try fetching ModelMetrics for *all* models, not just
        // new models, because the leaderboardFrame might have changed.
        updating.leaderboard_set_metrics = new IcedHashMap<>();
        Model aModel = null;
        for (Key<Model> aKey : updating.models) {
          aModel = aKey.get();
          if (null == aModel) {
            userFeedback.warn(UserFeedbackEvent.Stage.ModelTraining, "Model in the leaderboard has unexpectedly been deleted from H2O: " + aKey);
            continue;
          }

          // If leaderboardFrame is null, use xval metrics instead
          ModelMetrics mm = null;
          if (leaderboardFrame == null) {
            mm = aModel._output._cross_validation_metrics;
          } else {
            mm = ModelMetrics.getFromDKV(aModel, leaderboardFrame);
            if (mm == null) {
              //scores and magically stores the metrics where we're looking for it on the next line
              aModel.score(leaderboardFrame).delete();  // immediately delete the resulting frame to avoid leaks
              mm = ModelMetrics.getFromDKV(aModel, leaderboardFrame);
            }
          }
          if (mm != null) updating.leaderboard_set_metrics.put(mm._key, mm);
        }

        // Sort by metric on the leaderboard/test set or cross-validation metrics.
        try {
          List<Key<Model>> modelsSorted = null;
          if (leaderboardFrame == null) {
            modelsSorted = ModelMetrics.sortModelsByMetric(sort_metric, sort_decreasing, Arrays.asList(updating.models));
          } else {
            modelsSorted = ModelMetrics.sortModelsByMetric(leaderboardFrame, sort_metric, sort_decreasing, Arrays.asList(updating.models));
          }
          updating.models = modelsSorted.toArray(new Key[0]);
        } catch (H2OIllegalArgumentException e) {
          Log.warn("ModelMetrics.sortModelsByMetric failed: " + e);
          throw e;
        }

        Model[] updating_models = new Model[updating.models.length];
        modelsForModelKeys(updating.models, updating_models);

        updating.sort_metrics = getMetrics(updating.sort_metric, updating.leaderboard_set_metrics, leaderboardFrame, updating_models);

        if (aModel._output.isBinomialClassifier()) { // Binomial case
          updating.auc = getMetrics("auc", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.logloss = getMetrics("logloss", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.mean_per_class_error = getMetrics("mean_per_class_error", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.rmse = getMetrics("rmse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.mse = getMetrics("mse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
        } else if (aModel._output.isMultinomialClassifier()) { //Multinomial Case
          updating.mean_per_class_error = getMetrics("mean_per_class_error", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.logloss = getMetrics("logloss", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.rmse = getMetrics("rmse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.mse = getMetrics("mse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
        } else { //Regression Case
          updating.mean_residual_deviance= getMetrics("mean_residual_deviance", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.rmse = getMetrics("rmse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.mse = getMetrics("mse", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.mae = getMetrics("mae", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
          updating.rmsle = getMetrics("rmsle", updating.leaderboard_set_metrics, leaderboardFrame, updating_models);
        }

        // If we're updated leader let this know so that it can notify the user
        // (outside the tatomic, since it can take a long time).
        if (oldLeader == null || !oldLeader.equals(updating.models[0])) {
          newLeader[0] = updating.models[0];
          newLeaderSortMetric[0] = updating.sort_metrics[0];
        }

        return updating;
      } // atomic
    }.invoke(this._key);

    // We've updated the DKV but not this instance, so:
    Leaderboard updated = DKV.getGet(this._key);
    this.models = updated.models;
    this.leaderboard_set_metrics = updated.leaderboard_set_metrics;
    this.sort_metrics = updated.sort_metrics;
    if (updated.getLeader()._output.isBinomialClassifier()) { // Binomial case
      this.auc = updated.auc;
      this.logloss = updated.logloss;
      this.mean_per_class_error = updated.mean_per_class_error;
      this.rmse = updated.rmse;
      this.mse = updated.mse;
    } else if (updated.getLeader()._output.isMultinomialClassifier()) { // Multinomial case
      this.mean_per_class_error = updated.mean_per_class_error;
      this.logloss = updated.logloss;
      this.rmse = updated.rmse;
      this.mse = updated.mse;
    } else { // Regression case
      this.mean_residual_deviance = updated.mean_residual_deviance;
      this.rmse = updated.rmse;
      this.mse = updated.mse;
      this.mae = updated.mae;
      this.rmsle = updated.rmsle;
    }

    // always
    if (null != newLeader[0]) {
      userFeedback.info(UserFeedbackEvent.Stage.ModelTraining,
              "New leader: " + newLeader[0] + ", " + sort_metric + ": " + newLeaderSortMetric[0]);
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
  Key<Model>[] getModelKeys() {
    return ((Leaderboard)DKV.getGet(this._key)).models;
  }

  /**
   * @return list of keys of models sorted by the given metric, fetched from the DKV
   */
  private Key<Model>[] modelKeys(String metric, boolean sortDecreasing) {
    Key<Model>[] models = getModelKeys();
    List<Key<Model>> newModelsSorted =
            ModelMetrics.sortModelsByMetric(metric, sortDecreasing, Arrays.asList(models));
    return newModelsSorted.toArray(new Key[0]);
  }

  /**
   * @return list of models sorted by the default metric for the model category
   */
  Model[] getModels() {
    Key<Model>[] modelKeys = getModelKeys();

    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];

    Model[] models = new Model[modelKeys.length];
    return modelsForModelKeys(modelKeys, models);
  }

  /**
   * @return list of models sorted by the given metric
   */
  Model[] getModels(String metric, boolean sortDecreasing) {
    Key<Model>[] modelKeys = modelKeys(metric, sortDecreasing);

    if (modelKeys == null || 0 == modelKeys.length) return new Model[0];

    Model[] models = new Model[modelKeys.length];
    return modelsForModelKeys(modelKeys, models);
  }

  Model getLeader() {
    Key<Model>[] modelKeys = getModelKeys();

    if (modelKeys == null || 0 == modelKeys.length) return null;

    return modelKeys[0].get();
  }

  /** Return the number of models in this Leaderboard. */
  int getModelCount() { return getModelKeys().length; }

  private static double[] getMetrics(String metric, IcedHashMap<Key<ModelMetrics>, ModelMetrics> leaderboard_set_metrics, Frame leaderboardFrame, Model[] models) {
    double[] other_metrics = new double[models.length];
    int i = 0;
    for (Model m : models) {
      // If leaderboard frame exists, get metrics from there
      if (leaderboardFrame != null) {
        //System.out.println("@@@@@@@@@@@@@ Leaderboard frame metrics @@@@@@@@@@@@@");
        other_metrics[i++] = ModelMetrics.getMetricFromModelMetric(leaderboard_set_metrics.get(ModelMetrics.buildKey(m, leaderboardFrame)), metric);
      } else {
        // otherwise use cross-validation metrics
        //System.out.println("@@@@@@@@@@@@@ Cross-validation frame metrics @@@@@@@@@@@@@");
        Key model_key = m._key;
        long model_checksum = m.checksum();
        Key frame_key = m._output._cross_validation_metrics.frame()._key;
        long frame_checksum = m._output._cross_validation_metrics.frame().checksum();
        other_metrics[i++] = ModelMetrics.getMetricFromModelMetric(leaderboard_set_metrics.get(ModelMetrics.buildKey(model_key, model_checksum, frame_key, frame_checksum)), metric);
      }
    }
    return other_metrics;
  }

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  void delete() {
    for (Key k : leaderboard_set_metrics.keySet())
      k.remove();
    remove();
  }

  void deleteWithChildren() {
    for (Model m : getModels())
      m.delete();
    delete();
  }

  private static double[] defaultMetricForModel(Model m) {
    ModelMetrics mm =
            m._output._cross_validation_metrics != null ?
                    m._output._cross_validation_metrics :
                    m._output._validation_metrics != null ?
                            m._output._validation_metrics :
                            m._output._training_metrics;
    return defaultMetricForModel(m, mm);
  }

  private static double[] defaultMetricForModel(Model m, ModelMetrics mm) {
    if (m._output.isBinomialClassifier()) {
      return new double[] {(((ModelMetricsBinomial)mm).auc()),((ModelMetricsBinomial) mm).logloss(), ((ModelMetricsBinomial) mm).mean_per_class_error(), mm.rmse(), mm.mse()};
    } else if (m._output.isMultinomialClassifier()) {
      return new double[] {(((ModelMetricsMultinomial)mm).mean_per_class_error()), ((ModelMetricsMultinomial) mm).logloss(), mm.rmse(), mm.mse()};
    } else if (m._output.isSupervised()) {
      return new double[] {((ModelMetricsRegression)mm).mean_residual_deviance(),mm.rmse(), mm.mse(), ((ModelMetricsRegression) mm).mae(), ((ModelMetricsRegression) mm).rmsle()};
    }
    Log.warn("Failed to find metric for model: " + m);
    return new double[] {Double.NaN};
  }

  private static String[] defaultMetricNameForModel(Model m) {
    if (m._output.isBinomialClassifier()) {
      return new String[] {"auc","logloss", "mean_per_class_error", "rmse", "mse"};
    } else if (m._output.isMultinomialClassifier()) {
      return new String[] {"mean per-class error", "logloss", "rmse", "mse"};
    } else if (m._output.isSupervised()) {
      return new String[] {"mean_residual_deviance","rmse", "mse", "mae","rmsle"};
    }
    return new String[] {"unknown"};
  }

  String rankTsv() {
    String fieldSeparator = "\\t";
    String lineSeparator = "\\n";

    StringBuffer sb = new StringBuffer();
//  sb.append("Rank").append(fieldSeparator).append("Error").append(lineSeparator);
    sb.append("Error").append(lineSeparator);

    Model[] models = getModels();
    for (int i = models.length - 1; i >= 0; i--) {
      // TODO: allow the metric to be passed in.  Note that this assumes the validation (or training) frame is the same.
      Model m = models[i];
      sb.append(defaultMetricForModel(m));
      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  private static String[] colHeaders(String metric, String[] other_metric) {
    String[] headers = ArrayUtils.append(new String[]{"model_id",metric},other_metric);
    return headers;
  }

  private static final String[] colTypesMultinomial= {
          "string",
          "double",
          "double",
          "double",
          "double"
  };

  private static final String[] colFormatsMultinomial= {
          "%s",
          "%.6f",
          "%.6f",
          "%.6f",
          "%.6f"
  };

  private static final String[] colTypesBinomial= {
          "string",
          "double",
          "double",
          "double",
          "double",
          "double"
  };

  private static final String[] colFormatsBinomial= {
          "%s",
          "%.6f",
          "%.6f",
          "%.6f",
          "%.6f",
          "%.6f"
  };

  private static final String[] colTypesRegression= {
          "string",
          "double",
          "double",
          "double",
          "double",
          "double"
  };

  private static final String[] colFormatsRegression= {
          "%s",
          "%.6f",
          "%.6f",
          "%.6f",
          "%.6f",
          "%.6f"
  };

  private static final TwoDimTable makeTwoDimTable(String tableHeader, String sort_metric, String[] other_metrics, Model[] models) {
    assert sort_metric != null || models.length == 0 :
        "sort_metrics needs to be always not-null for non-empty array!";

    String[] rowHeaders = new String[models.length];
    for (int i = 0; i < models.length; i++) rowHeaders[i] = "" + i;

    if (models.length == 0) {
      // empty TwoDimTable
      return new TwoDimTable(tableHeader,
              "no models in this leaderboard",
              rowHeaders,
              Leaderboard.colHeaders("auc", other_metrics),
              Leaderboard.colTypesBinomial,
              Leaderboard.colFormatsBinomial,
              "-");
    }
    if(models[0]._output.isBinomialClassifier()) {
      //other_metrics =  new String[] {"logloss", "mean_per_class_error", "rmse", "mse"};
      return new TwoDimTable(tableHeader,
              "models sorted in order of " + sort_metric + ", best first",
              rowHeaders,
              Leaderboard.colHeaders("auc", other_metrics),
              Leaderboard.colTypesBinomial,
              Leaderboard.colFormatsBinomial,
              "#");
    } else if  (models[0]._output.isMultinomialClassifier()) {
      //other_metrics =  new String[] {"logloss", "rmse", "mse"};
      return new TwoDimTable(tableHeader,
              "models sorted in order of " + sort_metric + ", best first",
              rowHeaders,
              Leaderboard.colHeaders("mean_per_class_error", other_metrics),
              Leaderboard.colTypesMultinomial,
              Leaderboard.colFormatsMultinomial,
              "#");

    } else {
      //other_metrics = new String[] {"rmse", "mse", "mae","rmsle"};
      return new TwoDimTable(tableHeader,
              "models sorted in order of " + sort_metric + ", best first",
              rowHeaders,
              Leaderboard.colHeaders("mean_residual_deviance", other_metrics),
              Leaderboard.colTypesRegression,
              Leaderboard.colFormatsRegression,
              "#");
    }
  }


  private void addTwoDimTableRowMultinomial(TwoDimTable table, int row, String[] modelIDs, double[] mean_per_class_error, double[] logloss, double[] rmse, double[] mse) {
    int col = 0;
    table.set(row, col++, modelIDs[row]);
    //table.set(row, col++, timestampFormat.format(new Date(timestamps[row])));
    table.set(row, col++, mean_per_class_error[row]);
    table.set(row, col++, logloss[row]);
    table.set(row, col++, rmse[row]);
    table.set(row, col++, mse[row]);
  }

  private void addTwoDimTableRowBinomial(TwoDimTable table, int row, String[] modelIDs, double[] auc, double[] logloss, double[] mean_per_class_error, double[] rmse, double[] mse) {
    int col = 0;
    table.set(row, col++, modelIDs[row]);
    //table.set(row, col++, timestampFormat.format(new Date(timestamps[row])));
    table.set(row, col++, auc[row]);
    table.set(row, col++, logloss[row]);
    table.set(row, col++, mean_per_class_error[row]);
    table.set(row, col++, rmse[row]);
    table.set(row, col++, mse[row]);
  }

  private void addTwoDimTableRowRegression(TwoDimTable table, int row, String[] modelIDs, double[] mean_residual_deviance, double[] rmse, double[] mse, double[] mae, double[] rmsle) {
    int col = 0;
    table.set(row, col++, modelIDs[row]);
    //table.set(row, col++, timestampFormat.format(new Date(timestamps[row])));
    table.set(row, col++, mean_residual_deviance[row]);
    table.set(row, col++, rmse[row]);
    table.set(row, col++, mse[row]);
    table.set(row, col++, mae[row]);
    table.set(row, col++, rmsle[row]);
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("Leaderboard for project_name: " + project_name, false);
  }

  TwoDimTable toTwoDimTable(String tableHeader, boolean leftJustifyModelIds) {
    Model[] models = this.getModels();
    //long[] timestamps = getTimestamps(models);
    String[] modelIDsFormatted = new String[models.length];

    if (models.length == 0) { //No models due to exclude algos or ran out of time
      //Just use binomial metrics as a placeholder (no way to tell as user can pass in any metric to sort by)
      this.other_metrics = new String[] {"logloss", "mean_per_class_error", "rmse", "mse"};
    }
    else if(models[0]._output.isBinomialClassifier()) {
      this.other_metrics = new String[] {"logloss", "mean_per_class_error", "rmse", "mse"};
    } else if (models[0]._output.isMultinomialClassifier()) {
      this.other_metrics = new String[] {"logloss", "rmse", "mse"};
    } else {
      this.other_metrics = new String[] {"rmse", "mse", "mae","rmsle"};
    }

    TwoDimTable table = makeTwoDimTable(tableHeader, sort_metric, other_metrics, models);

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
      //addTwoDimTableRow(table, i, modelIDsFormatted, timestamps, sort_metrics);
      if(models[i]._output.isMultinomialClassifier()){ //Multinomial case
        addTwoDimTableRowMultinomial(table, i, modelIDsFormatted, mean_per_class_error, logloss, rmse, mse);
      }else if(models[i]._output.isBinomialClassifier()) { //Binomial case
        addTwoDimTableRowBinomial(table, i, modelIDsFormatted, auc, logloss, mean_per_class_error, rmse, mse);
      }else { //Regression
        addTwoDimTableRowRegression(table, i, modelIDsFormatted, mean_residual_deviance, rmse, mse, mae, rmsle);
      }
    return table;
  }

  //private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  private static String toString(String project_name, Model[] models, String fieldSeparator, String lineSeparator, boolean includeTitle, boolean includeHeader) {
    StringBuilder sb = new StringBuilder();
    if (includeTitle) {
      sb.append("Leaderboard for project_name \"")
              .append(project_name)
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
        sb.append("model_id");
        sb.append(fieldSeparator);

        sb.append(defaultMetricNameForModel(m));

        /*
        if (includeTimestamp) {
          sb.append(fieldSeparator);
          sb.append("timestamp");
        }
        */
        sb.append(lineSeparator);
        printedHeader = true;
      }

      sb.append(m._key.toString());
      sb.append(fieldSeparator);

      sb.append(defaultMetricForModel(m));

      /*
      if (includeTimestamp) {
        sb.append(fieldSeparator);
        sb.append(timestampFormat.format(m._output._end_time));
      }
      */

      sb.append(lineSeparator);
    }
    return sb.toString();
  }

  private String toString(String fieldSeparator, String lineSeparator) {
    return toString(project_name, getModels(), fieldSeparator, lineSeparator, true, true);
  }

  @Override
  public String toString() {
    return toString(" ; ", " | ");
  }
  
}
