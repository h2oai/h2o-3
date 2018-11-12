package ai.h2o.automl;

import ai.h2o.automl.UserFeedbackEvent.Stage;
import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper.StoppingMetric;
import hex.StackedEnsembleModel;
import hex.StackedEnsembleModel.StackedEnsembleParameters;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel.GLMParameters;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import hex.tree.drf.DRFModel.DRFParameters;
import hex.tree.gbm.GBMModel.GBMParameters;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMap;
import water.util.ArrayUtils;
import water.util.IcedHashMapGeneric;
import water.util.Log;
import water.util.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * H2O AutoML
 *
 * AutoML  is used for automating the machine learning workflow, which includes automatic training and
 * tuning of many models within a user-specified time-limit. Stacked Ensembles will be automatically
 * trained on collections of individual models to produce highly predictive ensemble models which, in most cases,
 * will be the top performing models in the AutoML Leaderboard.
 */
public final class AutoML extends Lockable<AutoML> implements TimedH2ORunnable {

  static class WorkAllocations extends Iced<WorkAllocations> {

    private static class Work extends Iced<Work> {
      private Algo algo;
      private int count;
      private JobType type;
      private int share;

      Work(Algo algo, int count, JobType type, int share) {
        this.algo = algo;
        this.count = count;
        this.type = type;
        this.share = share;
      }

      int consume() {
        return consume(1);
      }

      int consume(int amount) {
        int c = Math.min(this.count, amount);
        this.count -= c;
        return c * this.share;
      }

      int consumeAll() {
        return consume(Integer.MAX_VALUE);
      }
    }

    private boolean canAllocate = true;
    private final LinkedList<Work> allocations = new LinkedList<>();

    WorkAllocations allocate(Algo algo, int count, JobType type, int workShare) {
      if (!canAllocate) throw new IllegalStateException("can't allocate new work");

      allocations.add(new Work(algo, count, type, workShare));
      return this;
    }

    void end() {
      canAllocate = false;
    }

    void remove(Algo algo) {
      final Iterator<Work> iter = allocations.iterator();
      while (iter.hasNext()) {
        if (algo.equals(iter.next().algo)) iter.remove();
      }
    }

    Work getAllocation(Algo algo, JobType workType) {
      for (Work alloc : allocations) {
        if (alloc.algo == algo && alloc.type == workType) return alloc;
      }
      return null;
    }

    private int sum(List<Work> workItems) {
      int tot = 0;
      for (Work item : workItems) {
        tot += (item.count * item.share);
      }
      return tot;
    }

    int remainingWork() {
      return sum(allocations);
    }

    int remainingWork(JobType type) {
      List<Work> selected = new ArrayList<>(allocations.size());
      for (Work item : allocations) {
        if (item.type == type) selected.add(item);
      }
      return sum(selected);
    }

  }

  private static final String DISTRIBUTED_XGBOOST_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "automl.xgboost.multinode.enabled";

  private final static boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with
  private final static SimpleDateFormat fullTimestampFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.S");
  private final static SimpleDateFormat timestampFormatForKeys = new SimpleDateFormat("yyyyMMdd_HHmmss");

  /**
   * For external use only.
   * Keeping this mapping for existing users consuming the Java API directly.
   * Putting here only algos that were available in H2O 3.20.x,
   *  users that need to exclude new algos should switch to {@link Algo}.
   * @deprecated will be removed in H2O 3.24.x, please use {@link Algo} instead.
   */
  @Deprecated
  public interface algo extends Serializable {
    @Deprecated Algo GLM = Algo.GLM;
    @Deprecated Algo DRF = Algo.DRF;
    @Deprecated Algo GBM = Algo.GBM;
    @Deprecated Algo DeepLearning = Algo.DeepLearning;
    @Deprecated Algo StackedEnsemble = Algo.StackedEnsemble;
  }

  private enum JobType {
    Unknown,
    ModelBuild,
    HyperparamSearch
  }

  private AutoMLBuildSpec buildSpec;     // all parameters for doing this AutoML build
  private Frame origTrainingFrame;       // untouched original training frame
  private boolean didValidationSplit = false;
  private boolean didLeaderboardSplit = false;

  public AutoMLBuildSpec getBuildSpec() {
    return buildSpec;
  }

  public Frame getTrainingFrame() { return trainingFrame; }
  public Frame getValidationFrame() { return validationFrame; }
  public Frame getLeaderboardFrame() { return leaderboardFrame; }

  public Vec getResponseColumn() { return responseColumn; }
  public Vec getFoldColumn() { return foldColumn; }
  public Vec getWeightsColumn() { return weightsColumn; }

//  Disabling metadata collection for now as there is no use for it...
//  public FrameMetadata getFrameMetadata() {
////    return frameMetadata;
////  }

  private Frame trainingFrame;    // required training frame: can add and remove Vecs, but not mutate Vec data in place
  private Frame validationFrame;  // optional validation frame; the training_frame is split automagically if it's not specified
  private Frame leaderboardFrame; // optional test frame used for leaderboard scoring; if not specified, leaderboard will use xval metrics

  private Vec responseColumn;
  private Vec foldColumn;
  private Vec weightsColumn;

//  Disabling metadata collection for now as there is no use for it...
//  private FrameMetadata frameMetadata;           // metadata for trainingFrame

  private Key<Grid> gridKeys[] = new Key[0];  // Grid key for the GridSearches
//  private boolean isClassification;

  private Date startTime;
  private static Date lastStartTime; // protect against two runs with the same second in the timestamp; be careful about races
  private long stopTimeMs;
  private Job job;                  // the Job object for the build of this AutoML.  TODO: can we have > 1?

  private transient List<Job> jobs; // subjobs
  private transient List<Frame> tempFrames;

  private AtomicInteger modelCount = new AtomicInteger();  // prepare for concurrency
  private Leaderboard leaderboard;
  private UserFeedback userFeedback;

  // check that we haven't messed up the original Frame
  private Vec[] originalTrainingFrameVecs;
  private String[] originalTrainingFrameNames;
  private long[] originalTrainingFrameChecksums;

  private WorkAllocations workAllocations;

  private Algo[] skipAlgosList = new Algo[]{};

  public AutoML() {
    super(null);
  }

  // https://0xdata.atlassian.net/browse/STEAM-52  --more interesting user options
  public AutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {
    super(key);

    this.startTime = startTime;
    userFeedback = new UserFeedback(this); // Don't use until we set this.project_name

    this.buildSpec = buildSpec;

    userFeedback.info(Stage.Workflow, "AutoML job created: " + fullTimestampFormat.format(this.startTime));

    handleDatafileParameters(buildSpec);

    if (null != buildSpec.input_spec.fold_column && 5 != buildSpec.build_control.nfolds)
      throw new H2OIllegalArgumentException("Cannot specify fold_column and a non-default nfolds value at the same time.");
    if (null != buildSpec.input_spec.fold_column)
      userFeedback.warn(Stage.Workflow, "Custom fold column, " + buildSpec.input_spec.fold_column + ", will be used. nfolds value will be ignored.");

    userFeedback.info(Stage.Workflow, "Build control seed: " +
            buildSpec.build_control.stopping_criteria.seed() +
            (buildSpec.build_control.stopping_criteria.seed() == -1 ? " (random)" : ""));

    // By default, stopping tolerance is adaptive to the training frame
    if (this.buildSpec.build_control.stopping_criteria._stopping_tolerance == -1) {
      this.buildSpec.build_control.stopping_criteria.set_default_stopping_tolerance_for_frame(this.trainingFrame);
      userFeedback.info(Stage.Workflow, "Setting stopping tolerance adaptively based on the training frame: " +
              this.buildSpec.build_control.stopping_criteria._stopping_tolerance);
    } else {
      userFeedback.info(Stage.Workflow, "Stopping tolerance set by the user: " + this.buildSpec.build_control.stopping_criteria._stopping_tolerance);

      double default_tolerance = RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(this.trainingFrame);
      if (this.buildSpec.build_control.stopping_criteria._stopping_tolerance < 0.7 * default_tolerance){
        userFeedback.warn(Stage.Workflow, "Stopping tolerance set by the user is < 70% of the recommended default of " + default_tolerance + ", so models may take a long time to converge or may not converge at all.");
      }
    }

    userFeedback.info(Stage.Workflow, "Project: " + projectName());

    String sort_metric = buildSpec.input_spec.sort_metric == null ? null : buildSpec.input_spec.sort_metric.toLowerCase();
    // TODO: does this need to be updated?  I think its okay to pass a null leaderboardFrame
    leaderboard = Leaderboard.getOrMakeLeaderboard(projectName(), userFeedback, this.leaderboardFrame, sort_metric);

    this.jobs = new ArrayList<>();
    this.tempFrames = new ArrayList<>();
  }


  WorkAllocations planWork() {
    workAllocations = new WorkAllocations();
    workAllocations.allocate(Algo.DeepLearning, 1, JobType.ModelBuild, 10)
            .allocate(Algo.DeepLearning, 3, JobType.HyperparamSearch, 20)
            .allocate(Algo.DRF, 2, JobType.ModelBuild, 10)
            .allocate(Algo.GBM, 5, JobType.ModelBuild, 10)
            .allocate(Algo.GBM, 1, JobType.HyperparamSearch, 60)
            .allocate(Algo.GLM, 1, JobType.HyperparamSearch, 20)
            .allocate(Algo.XGBoost, 3, JobType.ModelBuild, 10)
            .allocate(Algo.XGBoost, 1, JobType.HyperparamSearch, 100)
            .allocate(Algo.StackedEnsemble, 2, JobType.ModelBuild, 15)
            .end();

    if (buildSpec.build_models.exclude_algos != null) {
      for (Algo algo : (Algo[]) buildSpec.build_models.exclude_algos) {
        skipAlgosList = ArrayUtils.append(skipAlgosList, algo);
      }
    }
    if (!ExtensionManager.getInstance().isCoreExtensionEnabled("XGBoost")
        || (H2O.CLOUD.size() > 1 && !Boolean.parseBoolean(System.getProperty(DISTRIBUTED_XGBOOST_ENABLED, "false")))) {
      userFeedback.warn(Stage.ModelTraining, "AutoML: XGBoost extension is not available; skipping default XGBoost");
      skipAlgosList = ArrayUtils.append(skipAlgosList, Algo.XGBoost);
    }

    // This is useful during debugging.
//    skipAlgosList = ArrayUtils.append(skipAlgosList, Algo.GLM, Algo.DRF, Algo.GBM, Algo.DeepLearning, Algo.StackedEnsemble);

    // Inform the user about skipped algos.
    // Note: to make the keys short we use "DL" for the "DeepLearning" searches:
    for (Algo skippedAlgo : skipAlgosList) {
      userFeedback.info(Stage.ModelTraining, "Disabling Algo: " + skippedAlgo + " as requested by the user.");
      workAllocations.remove(skippedAlgo);
    }

    return workAllocations;
  }

  /**
   * If the user hasn't specified validation data, split it off for them.
   *                                                                  <p>
   * For nfolds > 1, the user can specify:                            <p>
   * 1. training only                                                 <p>
   * 2. training + leaderboard                                        <p>
   * 3. training + validation                                         <p>
   * 4. training + validation + leaderboard                           <p>
   *                                                                  <p>
   * In the top two cases we auto-split:                              <p>
   * training -> training:validation  80:20                           <p>
   *                                                                  <p>
   * For nfolds = 0, we have different rules:                         <p>
   * 5. training only                                                 <p>
   * 6. training + leaderboard                                        <p>
   * 7. training + validation                                         <p>
   * 8. training + validation + leaderboard                           <p>
   *                                                                  <p>
   * TODO: should the size of the splits adapt to origTrainingFrame.numRows()?
   */
  private void optionallySplitDatasets() {
     // TODO: Maybe clean this up a bit -- use else if instead of nested if/else
    // If using cross-validation (via nfolds or fold_column), we can use CV metrics for the Leaderboard
    // therefore we don't need to auto-gen a leaderboard frame
    if (this.buildSpec.build_control.nfolds > 1 || null != this.buildSpec.input_spec.fold_column) {
      if (null == this.validationFrame) {
        // case 1 and 2: missing validation frame -- need to create validation frame
        Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(origTrainingFrame,
                new Key[] { Key.make("automl_training_" + origTrainingFrame._key),
                        Key.make("automl_validation_" + origTrainingFrame._key)},
                new double[] { 0.9, 0.1 },
                buildSpec.build_control.stopping_criteria.seed());
        this.trainingFrame = splits[0];
        this.validationFrame = splits[1];
        this.didValidationSplit = true;
        this.didLeaderboardSplit = false;
        userFeedback.info(Stage.DataImport, "Automatically split the training data into training and validation frames in the ratio 80/20");
      } else {
        // case 3 and 4: nothing to do here
        userFeedback.info(Stage.DataImport, "Training and validation were both specified; no auto-splitting.");
      }
      if (null == this.leaderboardFrame) {
        // Extra logging for null leaderboard_frame (case 1 and 3)
        userFeedback.info(Stage.DataImport, "Leaderboard frame not provided by the user; leaderboard will use cross-validation metrics instead.");
      }
    } else {
      // If not using cross-validation, then we must auto-gen a leaderboard frame (and validation frame if missing)
      if (null == this.leaderboardFrame) {
        if (null == this.validationFrame) {
          // case 5: no CV, missing validation and leaderboard frames -- need to create them both from train
          Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(origTrainingFrame,
                  new Key[] { Key.make("automl_training_" + origTrainingFrame._key),
                          Key.make("automl_validation_" + origTrainingFrame._key),
                          Key.make("automl_leaderboard_" + origTrainingFrame._key)},
                  new double[] { 0.8, 0.1, 0.1 },
                  buildSpec.build_control.stopping_criteria.seed());
          this.trainingFrame = splits[0];
          this.validationFrame = splits[1];
          this.leaderboardFrame = splits[2];
          this.didValidationSplit = true;
          this.didLeaderboardSplit = true;
          userFeedback.info(Stage.DataImport, "Automatically split the training data into training, validation and leaderboard frames in the ratio 80/10/10");
        } else {
          // case 7: no CV, missing leaderboard frame but validation exists -- need to create leaderboard frame from valid
          Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(validationFrame,
                  new Key[] { Key.make("automl_validation_" + origTrainingFrame._key),
                          Key.make("automl_leaderboard_" + origTrainingFrame._key)},
                  new double[] { 0.5, 0.5 },
                  buildSpec.build_control.stopping_criteria.seed());
          this.validationFrame = splits[0];
          this.leaderboardFrame = splits[1];
          this.didValidationSplit = true;
          this.didLeaderboardSplit = true;
          userFeedback.info(Stage.DataImport, "Automatically split the validation data into validation and leaderboard frames in the ratio 50/50");
        }
      } else {
        // leaderboard frame is there, so if missing valid, then we just need to do a 90/10 split, else do nothing
        if (null == this.validationFrame) {
          // case 6: no CV, missing validation -- need to create it from train
          Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(origTrainingFrame,
                  new Key[] { Key.make("automl_training_" + origTrainingFrame._key),
                          Key.make("automl_validation_" + origTrainingFrame._key)},
                  new double[] { 0.9, 0.1 },
                  buildSpec.build_control.stopping_criteria.seed());
          this.trainingFrame = splits[0];
          this.validationFrame = splits[1];
          this.didValidationSplit = true;
          this.didLeaderboardSplit = false;
          userFeedback.info(Stage.DataImport, "Automatically split the training data into training and validation frames in the ratio 80/20");
        } else {
          // case 8: all frames are there, no need to do anything
          userFeedback.info(Stage.DataImport, "Training, validation and leaderboard datasets were all specified; not auto-splitting.");
        }
      }
    }
  }

  private void handleDatafileParameters(AutoMLBuildSpec buildSpec) {
    this.origTrainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
    this.validationFrame = DKV.getGet(buildSpec.input_spec.validation_frame);
    this.leaderboardFrame = DKV.getGet(buildSpec.input_spec.leaderboard_frame);

    if (this.origTrainingFrame.find(buildSpec.input_spec.response_column) == -1) {
      throw new H2OIllegalArgumentException("Response column '" + buildSpec.input_spec.response_column + "' is not in " +
              "the training frame.");
    }

    if(this.validationFrame != null && this.validationFrame.find(buildSpec.input_spec.response_column) == -1) {
      throw new H2OIllegalArgumentException("Response column '" + buildSpec.input_spec.response_column + "' is not in " +
              "the validation frame.");
    }

    if(this.leaderboardFrame != null && this.leaderboardFrame.find(buildSpec.input_spec.response_column) == -1) {
      throw new H2OIllegalArgumentException("Response column '" + buildSpec.input_spec.response_column + "' is not in " +
              "the leaderboard frame.");
    }

    if (buildSpec.input_spec.fold_column != null && this.origTrainingFrame.find(buildSpec.input_spec.fold_column) == -1) {
      throw new H2OIllegalArgumentException("Fold column '" + buildSpec.input_spec.fold_column + "' is not in " +
              "the training frame.");
    }
    if (buildSpec.input_spec.weights_column != null && this.origTrainingFrame.find(buildSpec.input_spec.weights_column) == -1) {
      throw new H2OIllegalArgumentException("Weights column '" + buildSpec.input_spec.weights_column + "' is not in " +
              "the training frame.");
    }

    optionallySplitDatasets();

    if (null == this.trainingFrame) {
      // we didn't need to split off the validation_frame or leaderboard_frame ourselves
      this.trainingFrame = new Frame(origTrainingFrame);
      DKV.put(this.trainingFrame);
    }

    this.responseColumn = trainingFrame.vec(buildSpec.input_spec.response_column);
    this.foldColumn = trainingFrame.vec(buildSpec.input_spec.fold_column);
    this.weightsColumn = trainingFrame.vec(buildSpec.input_spec.weights_column);

    this.userFeedback.info(Stage.DataImport, "training frame: " + this.trainingFrame.toString().replace("\n", " ") + " checksum: " + this.trainingFrame.checksum());
    this.userFeedback.info(Stage.DataImport, "validation frame: " + this.validationFrame.toString().replace("\n", " ") + " checksum: " + this.validationFrame.checksum());
    if (null != this.leaderboardFrame) {
      this.userFeedback.info(Stage.DataImport, "leaderboard frame: " + this.leaderboardFrame.toString().replace("\n", " ") + " checksum: " + this.leaderboardFrame.checksum());
    } else {
      this.userFeedback.info(Stage.DataImport, "leaderboard frame: NULL");
    }

    this.userFeedback.info(Stage.DataImport, "response column: " + buildSpec.input_spec.response_column);
    this.userFeedback.info(Stage.DataImport, "fold column: " + this.foldColumn);
    this.userFeedback.info(Stage.DataImport, "weights column: " + this.weightsColumn);

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      originalTrainingFrameVecs = origTrainingFrame.vecs().clone();
      originalTrainingFrameNames = origTrainingFrame.names().clone();
      originalTrainingFrameChecksums = new long[originalTrainingFrameVecs.length];

      for (int i = 0; i < originalTrainingFrameVecs.length; i++)
        originalTrainingFrameChecksums[i] = originalTrainingFrameVecs[i].checksum();
    }
    DKV.put(this);
  }


  public static AutoML makeAutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {

    AutoML autoML = new AutoML(key, startTime, buildSpec);

    if (null == autoML.trainingFrame)
      throw new H2OIllegalArgumentException("No training data has been specified, either as a path or a key.");

    return autoML;
  }

  // used to launch the AutoML asynchronously
  @Override
  public void run() {
    stopTimeMs = System.currentTimeMillis() + Math.round(1000 * buildSpec.build_control.stopping_criteria.max_runtime_secs());
    learn();
  }

  @Override
  public void stop() {
    for (Frame f : tempFrames) f.delete();
    tempFrames = null;

    if (null == jobs) return; // already stopped
    for (Job j : jobs) j.stop();
    for (Job j : jobs) j.get(); // Hold until they all completely stop.
    jobs = null;

    // TODO: add a failsafe, if we haven't marked off as much work as we originally intended?
    // If we don't, we end up with an exceptional completion.
  }

  public long getStopTimeMs() {
    return stopTimeMs;
  }

  public long timeRemainingMs() {
    if (getStopTimeMs() < 0) return Long.MAX_VALUE;
    long remaining = getStopTimeMs() - System.currentTimeMillis();
    return Math.max(0, remaining);
  }

  public int remainingModels() {
    if (buildSpec.build_control.stopping_criteria.max_models() == 0)
      return Integer.MAX_VALUE;
    return buildSpec.build_control.stopping_criteria.max_models() - modelCount.get();
  }

  private boolean timingOut() {
    return timeRemainingMs() <= 0;
  }

  @Override
  public boolean keepRunning() {
    return timeRemainingMs() > 0 && remainingModels() > 0;
  }

  private void pollAndUpdateProgress(Stage stage, String name, WorkAllocations.Work work, Job parentJob, Job subJob) {
    pollAndUpdateProgress(stage, name, work, parentJob, subJob, false);
  }

  private void pollAndUpdateProgress(Stage stage, String name, WorkAllocations.Work work, Job parentJob, Job subJob, boolean ignoreTimeout) {
    if (null == subJob) {
      if (null != parentJob) {
        parentJob.update(work.consume(), "SKIPPED: " + name);
        Log.info("AutoML skipping " + name);
      }
      return;
    }
    userFeedback.info(stage, name + " started");
    jobs.add(subJob);

    long lastWorkedSoFar = 0;
    int gridLastCount = 0;

    while (subJob.isRunning()) {
      if (null != parentJob) {
        if (parentJob.stop_requested()) {
          userFeedback.info(Stage.ModelTraining, "AutoML job cancelled; skipping " + name);
          subJob.stop();
        }
        if (!ignoreTimeout && timingOut()) {
          userFeedback.info(Stage.ModelTraining, "AutoML: out of time; skipping " + name);
          subJob.stop();
        }
      }
      long workedSoFar = Math.round(subJob.progress() * work.share);

      if (null != parentJob) {
        parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), name);
      }

      if (JobType.HyperparamSearch == work.type) {
        Grid grid = (Grid)subJob._result.get();
        int gridCount = grid.getModelCount();
        if (gridCount > gridLastCount) {
          userFeedback.info(Stage.ModelTraining, "Built: " + gridCount + " models for search: " + name);
          this.addModels(grid.getModelKeys());
          gridLastCount = gridCount;
        }
      }

      try {
        Thread.sleep(1000);
      }
      catch (InterruptedException e) {
        // keep going
      }
      lastWorkedSoFar = workedSoFar;
    }

    // pick up any stragglers:
    if (JobType.HyperparamSearch == work.type) {
      if (subJob.isCrashed()) {
        userFeedback.info(stage, name + " failed: " + subJob.ex().toString());
      } else if (subJob.get() == null) {
        userFeedback.info(stage, name + " cancelled");
      } else {
        Grid grid = (Grid) subJob.get();
        int gridCount = grid.getModelCount();
        if (gridCount > gridLastCount) {
          userFeedback.info(Stage.ModelTraining, "Built: " + gridCount + " models for search: " + name);
          this.addModels(grid.getModelKeys());
        }
        userFeedback.info(stage, name + " complete");
      }
    } else if (JobType.ModelBuild == work.type) {
      if (subJob.isCrashed()) {
        userFeedback.info(stage, name + " failed: " + subJob.ex().toString());
      } else if (subJob.get() == null) {
        userFeedback.info(stage, name + " cancelled");
      } else {
        userFeedback.info(stage, name + " complete");
        this.addModel((Model) subJob.get());
      }
    }

    // add remaining work
    if (null != parentJob) {
      parentJob.update(work.share - lastWorkedSoFar);
    }
    work.consume();

    jobs.remove(subJob);
  }

  // These are per (possibly concurrent) AutoML run.
  // All created keys for a run use the unique AutoML
  // run timestamp, so we can't have name collisions.
  private int individualModelsTrained = 0;
  private NonBlockingHashMap<String, Integer> algoInstanceCounters = new NonBlockingHashMap<>();
  private NonBlockingHashMap<String, Integer> gridInstanceCounters = new NonBlockingHashMap<>();

  private int nextInstanceCounter(String algoName, NonBlockingHashMap<String, Integer> instanceCounters) {
    synchronized (instanceCounters) {
      int instanceNum = 1;
      if (instanceCounters.containsKey(algoName))
        instanceNum = instanceCounters.get(algoName) + 1;
      instanceCounters.put(algoName, instanceNum);
      return instanceNum;
    }
  }

  private Key<Model> modelKey(String algoName) {
    return modelKey(algoName, true);
  }

  private Key<Model> modelKey(String algoName, boolean with_counter) {
    String counterStr = with_counter ? "_" + nextInstanceCounter(algoName, algoInstanceCounters) : "";
    return Key.make(algoName + counterStr + "_AutoML_" + timestampFormatForKeys.format(this.startTime));
  }

  Job<Model> trainModel(Key<Model> key, WorkAllocations.Work work, Model.Parameters parms) {
    return trainModel(key, work, parms, false);
  }

  /**
   * @param key (optional) model key
   * @param work  the allocated work item: used to check various executions limits and to distribute remaining work (time + models)
   * @param parms the model builder params
   * @param ignoreLimits (defaults to false) whether or not to ignore the max_models/max_runtime constraints
   * @return a started training model
   */
  Job<Model> trainModel(Key<Model> key, WorkAllocations.Work work, Model.Parameters parms, boolean ignoreLimits) {
    if (exceededSearchLimits(work, key == null ? null : key.toString(), ignoreLimits)) return null;

    Algo algo = work.algo;
    String algoName = ModelBuilder.algoName(algo.urlName());

    if (null == key) key = modelKey(algoName);

    Job<Model> job = new Job<>(key, ModelBuilder.javaName(algo.urlName()), algoName);
    ModelBuilder builder = ModelBuilder.make(algo.urlName(), job, key);
    Model.Parameters defaults = builder._parms;
    builder._parms = parms;

    setCommonModelBuilderParams(builder._parms);

    if (ignoreLimits)
      builder._parms._max_runtime_secs = 0;
    else if (builder._parms._max_runtime_secs == 0)
      builder._parms._max_runtime_secs = Math.round(timeRemainingMs() / 1000.0);
    else
      builder._parms._max_runtime_secs = Math.min(builder._parms._max_runtime_secs, Math.round(timeRemainingMs() / 1000.0));

    setStoppingCriteria(parms, defaults);

    // If we have set a seed for the search and not for the individual model params
    // then use a sequence starting with the same seed given for the model build.
    // Don't use the same exact seed so that, e.g., if we build two GBMs they don't
    // do the same row and column sampling.
    if (builder._parms._seed == defaults._seed && buildSpec.build_control.stopping_criteria.seed() != -1)
      builder._parms._seed = buildSpec.build_control.stopping_criteria.seed() + individualModelsTrained++;

    builder.init(false);          // validate parameters

    // TODO: handle error_count and messages

    Log.debug("Training model: " + algoName + ", time remaining (ms): " + timeRemainingMs());
    try {
      return builder.trainModelOnH2ONode();
    } catch (H2OIllegalArgumentException exception) {
      userFeedback.warn(Stage.ModelTraining, "Skipping training of model "+key+" due to exception: "+exception);
      return null;
    }
  }

  private Key<Grid> gridKey(String algoName) {
    return Key.make(algoName + "_grid_" + nextInstanceCounter(algoName, gridInstanceCounters) + "_AutoML_" + timestampFormatForKeys.format(this.startTime));
  }

  private void addGridKey(Key<Grid> gridKey) {
    gridKeys = Arrays.copyOf(gridKeys, gridKeys.length + 1);
    gridKeys[gridKeys.length - 1] = gridKey;
  }

  /**
   * Do a random hyperparameter search.  Caller must eventually do a <i>get()</i>
   * on the returned Job to ensure that it's complete.
   * @param gridKey optional grid key
   * @param work  the allocated work item: used to check various executions limits and to distribute remaining work (time + models)
   * @param baseParms ModelBuilder parameter values that are common across all models in the search
   * @param searchParms hyperparameter search space
   * @return the started hyperparameter search job
   */
  Job<Grid> hyperparameterSearch(
      Key<Grid> gridKey, WorkAllocations.Work work, Model.Parameters baseParms, Map<String, Object[]> searchParms
  ) {
    if (exceededSearchLimits(work)) return null;

    Algo algo = work.algo;
    setCommonModelBuilderParams(baseParms);

    RandomDiscreteValueSearchCriteria searchCriteria = (RandomDiscreteValueSearchCriteria)buildSpec.build_control.stopping_criteria.clone();
    float remainingWorkRatio = (float) work.share / workAllocations.remainingWork();
//    float remainingWorkRatio = (float) work.workShare / workAllocations.remainingWork(work.jobType);
    long maxAssignedTime = (long) Math.round(remainingWorkRatio * timeRemainingMs() / 1000);
    int maxAssignedModels = (int) Math.ceil(remainingWorkRatio * remainingModels());

    if (searchCriteria.max_runtime_secs() == 0)
      searchCriteria.set_max_runtime_secs(maxAssignedTime);
    else
      searchCriteria.set_max_runtime_secs(Math.min(searchCriteria.max_runtime_secs(), maxAssignedTime));

    if (searchCriteria.max_models() == 0)
      searchCriteria.set_max_models(maxAssignedModels);
    else
      searchCriteria.set_max_models(Math.min(searchCriteria.max_models(), maxAssignedModels));

    userFeedback.info(Stage.ModelTraining, "AutoML: starting " + algo + " hyperparameter search");

    // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
    Model.Parameters defaults;
    try {
      defaults = baseParms.getClass().newInstance();
    } catch (Exception e) {
      userFeedback.warn(Stage.ModelTraining, "Internal error doing hyperparameter search");
      throw new H2OIllegalArgumentException("Hyperparameter search can't create a new instance of Model.Parameters subclass: " + baseParms.getClass());
    }

    setStoppingCriteria(baseParms, defaults);

    // NOTE:
    // RandomDiscrete Hyperparameter Search matches the logic used in #trainModel():
    // If we have set a seed for the search and not for the individual model params
    // then use a sequence starting with the same seed given for the model build.
    // Don't use the same exact seed so that, e.g., if we build two GBMs they don't
    // do the same row and column sampling.
    if (null == gridKey) gridKey = gridKey(algo.name());
    addGridKey(gridKey);
    Log.debug("Hyperparameter search: " + algo.name() + ", time remaining (ms): " + timeRemainingMs());
    return GridSearch.startGridSearch(
        gridKey,
        baseParms,
        searchParms,
        new GridSearch.SimpleParametersBuilderFactory(),
        searchCriteria
    );
  }

  private void setCommonModelBuilderParams(Model.Parameters params) {
    params._train = trainingFrame._key;
    if (null != validationFrame)
      params._valid = validationFrame._key;
    params._response_column = buildSpec.input_spec.response_column;
    params._ignored_columns = buildSpec.input_spec.ignored_columns;
    params._seed = buildSpec.build_control.stopping_criteria.seed();

    // currently required, for the base_models, for stacking:
    if (! (params instanceof StackedEnsembleParameters)) {
      params._keep_cross_validation_predictions = true;

      // TODO: StackedEnsemble doesn't support weights yet in score0
      params._fold_column = buildSpec.input_spec.fold_column;
      params._weights_column = buildSpec.input_spec.weights_column;

      if (buildSpec.input_spec.fold_column == null) {
        params._nfolds = buildSpec.build_control.nfolds;
        if (buildSpec.build_control.nfolds > 1) {
          // TODO: below allow the user to specify this (vs Modulo)
          // TODO: also, the docs currently say that we use Random folds... not Modulo
          params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
        }
      }
      if (buildSpec.build_control.balance_classes == true) {
        params._balance_classes = buildSpec.build_control.balance_classes;
        params._class_sampling_factors = buildSpec.build_control.class_sampling_factors;
        params._max_after_balance_size = buildSpec.build_control.max_after_balance_size;
      }
      //TODO: add a check that gives an error when class_sampling_factors, max_after_balance_size is set and balance_classes = false
    }

    params._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
    params._keep_cross_validation_fold_assignment = buildSpec.build_control.nfolds != 0 && buildSpec.build_control.keep_cross_validation_fold_assignment;
    params._export_checkpoints_dir = buildSpec.build_control.export_checkpoints_dir;
  }

  private void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults) {
    // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.

    //FIXME: Do we really need to compare with defaults before setting the buildSpec value instead?
    // This can create subtle bugs: e.g. if dev wanted to enforce a stopping criteria for a specific algo/model,
    // he wouldn't be able to enforce the default value, that would always be overridden by buildSpec.
    // We should instead provide hooks and ensure that properties are always set in the following order:
    //  1. defaults, 2. user defined, 3. internal logic/algo specific based on the previous state (esp. handling of AUTO properties).
    if (parms._stopping_metric == defaults._stopping_metric)
      parms._stopping_metric = buildSpec.build_control.stopping_criteria.stopping_metric();

    if (parms._stopping_metric == StoppingMetric.AUTO) {
      String sort_metric = getSortMetric();
      parms._stopping_metric = sort_metric == null ? StoppingMetric.AUTO
                              : sort_metric.equals("auc") ? StoppingMetric.logloss
                              : metricValueOf(sort_metric);
    }

    if (parms._stopping_rounds == defaults._stopping_rounds)
      parms._stopping_rounds = buildSpec.build_control.stopping_criteria.stopping_rounds();

    if (parms._stopping_tolerance == defaults._stopping_tolerance)
      parms._stopping_tolerance = buildSpec.build_control.stopping_criteria.stopping_tolerance();
  }

  private boolean exceededSearchLimits(WorkAllocations.Work work) {
    return exceededSearchLimits(work, null, false);
  }

  private boolean exceededSearchLimits(WorkAllocations.Work work, String algo_desc, boolean ignoreLimits) {
    String fullName = algo_desc == null ? work.algo.toString() : work.algo+" ("+algo_desc+")";
    if (!ignoreLimits && timingOut()) {
      userFeedback.info(Stage.ModelTraining, "AutoML: out of time; skipping "+fullName+" in "+work.type);
      return true;
    }

    if (!ignoreLimits && remainingModels() <= 0) {
      userFeedback.info(Stage.ModelTraining, "AutoML: hit the max_models limit; skipping "+fullName+" in "+work.type);
      return true;
    }
    return false;
  }

  void defaultXGBoosts(boolean emulateLightGBM) {
    Algo algo = Algo.XGBoost;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.ModelBuild);
    if (work == null) return;

    XGBoostParameters xgBoostParameters = new XGBoostParameters();
    setCommonModelBuilderParams(xgBoostParameters);

    Job xgBoostJob;
    Key<Model> key;

    if (emulateLightGBM) {
      xgBoostParameters._tree_method = XGBoostParameters.TreeMethod.hist;
      xgBoostParameters._grow_policy = XGBoostParameters.GrowPolicy.lossguide;
    }

    // setDistribution: no way to identify gaussian, poisson, laplace? using descriptive statistics?
    xgBoostParameters._distribution = getResponseColumn().isBinary() && !(getResponseColumn().isNumeric()) ? DistributionFamily.bernoulli
                    : getResponseColumn().isCategorical() ? DistributionFamily.multinomial
                    : DistributionFamily.AUTO;

    xgBoostParameters._score_tree_interval = 5;
    xgBoostParameters._stopping_rounds = 5;
//    xgBoostParameters._stopping_tolerance = Math.min(1e-2, RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(this.trainingFrame));

    xgBoostParameters._ntrees = 10000;
    xgBoostParameters._learn_rate = 0.05;
//    xgBoostParameters._min_split_improvement = 0.01f;

    //XGB 1
    xgBoostParameters._max_depth = 5;
    xgBoostParameters._min_rows = 3;
    xgBoostParameters._sample_rate = 0.8;
    xgBoostParameters._col_sample_rate = 0.8;
    xgBoostParameters._col_sample_rate_per_tree = 0.8;

    if (emulateLightGBM) {
      xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
      xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
//      xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
      xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
    }

    key = modelKey(algo.name());
    xgBoostJob = trainModel(key, work, xgBoostParameters);
    pollAndUpdateProgress(Stage.ModelTraining,  key.toString(), work, this.job(), xgBoostJob);

    //XGB 2
    xgBoostParameters._max_depth = 10;
    xgBoostParameters._min_rows = 5;
    xgBoostParameters._sample_rate = 0.6;
    xgBoostParameters._col_sample_rate = 0.8;
    xgBoostParameters._col_sample_rate_per_tree = 0.8;

    if (emulateLightGBM) {
      xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
      xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
//      xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
      xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
    }

    key = modelKey(algo.name());
    xgBoostJob = trainModel(key, work, xgBoostParameters);
    pollAndUpdateProgress(Stage.ModelTraining,  key.toString(), work, this.job(), xgBoostJob);

    //XGB 3
    xgBoostParameters._max_depth = 20;
    xgBoostParameters._min_rows = 10;
    xgBoostParameters._sample_rate = 0.6;
    xgBoostParameters._col_sample_rate = 0.8;
    xgBoostParameters._col_sample_rate_per_tree = 0.8;

    if (emulateLightGBM) {
      xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
      xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
//      xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
      xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
    }

    key = modelKey(algo.name());
    xgBoostJob = trainModel(key, work, xgBoostParameters);
    pollAndUpdateProgress(Stage.ModelTraining,  key.toString(), work, this.job(), xgBoostJob);
  }


  void defaultSearchXGBoost(Key<Grid> gridKey, boolean emulateLightGBM) {
    Algo algo = Algo.XGBoost;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    XGBoostParameters xgBoostParameters = new XGBoostParameters();
    setCommonModelBuilderParams(xgBoostParameters);


    if (emulateLightGBM) {
       xgBoostParameters._tree_method = XGBoostParameters.TreeMethod.hist;
      xgBoostParameters._grow_policy = XGBoostParameters.GrowPolicy.lossguide;
    }

    xgBoostParameters._distribution = getResponseColumn().isBinary() && !(getResponseColumn().isNumeric()) ? DistributionFamily.bernoulli
            : getResponseColumn().isCategorical() ? DistributionFamily.multinomial
            : DistributionFamily.AUTO;

    xgBoostParameters._score_tree_interval = 5;
    xgBoostParameters._stopping_rounds = 5;
//    xgBoostParameters._stopping_tolerance = Math.min(1e-2, RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(this.trainingFrame));

    xgBoostParameters._ntrees = 10000;
    xgBoostParameters._learn_rate = 0.05;
//    xgBoostParameters._min_split_improvement = 0.01f; //DAI default

    Map<String, Object[]> searchParams = new HashMap<>();
//    searchParams.put("_ntrees", new Integer[]{100, 1000, 10000}); // = _n_estimators

    if (emulateLightGBM) {
      searchParams.put("_max_leaves", new Integer[]{1<<5, 1<<10, 1<<15, 1<<20});
      searchParams.put("_max_depth", new Integer[]{10, 20, 50});
      searchParams.put("_min_sum_hessian_in_leaf", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});
    } else {
      searchParams.put("_max_depth", new Integer[]{5, 10, 15, 20});
      searchParams.put("_min_rows", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
    }

    searchParams.put("_sample_rate", new Double[]{0.6, 0.8, 1.0}); // = _subsample
    searchParams.put("_col_sample_rate" , new Double[]{ 0.6, 0.8, 1.0}); // = _colsample_bylevel"
    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.7, 0.8, 0.9, 1.0}); // = _colsample_bytree: start higher to always use at least about 40% of columns
//    searchParams.put("_learn_rate", new Double[]{0.01, 0.05, 0.08, 0.1, 0.15, 0.2, 0.3, 0.5, 0.8, 1.0}); // = _eta
//    searchParams.put("_min_split_improvement", new Float[]{0.01f, 0.05f, 0.1f, 0.5f, 1f, 5f, 10f, 50f}); // = _gamma
//    searchParams.put("_tree_method", new XGBoostParameters.TreeMethod[]{XGBoostParameters.TreeMethod.auto});
    searchParams.put("_booster", new XGBoostParameters.Booster[]{ //gblinear crashes currently
            XGBoostParameters.Booster.gbtree, //default, let's use it more often
            XGBoostParameters.Booster.gbtree,
            XGBoostParameters.Booster.dart
    });

    searchParams.put("_reg_lambda", new Float[]{0.001f, 0.01f, 0.1f, 1f, 10f, 100f});
    searchParams.put("_reg_alpha", new Float[]{0.001f, 0.01f, 0.1f, 0.5f, 1f});

    Job<Grid> xgBoostSearchJob = hyperparameterSearch(gridKey, work, xgBoostParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, algo.name()+" hyperparameter search", work, this.job(), xgBoostSearchJob);
  }


  void defaultRandomForest() {
    Algo algo = Algo.DRF;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.ModelBuild);
    if (work == null) return;

    DRFParameters drfParameters = new DRFParameters();
    setCommonModelBuilderParams(drfParameters);
    drfParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();

    Job randomForestJob = trainModel(null, work, drfParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "Default Random Forest build", work, this.job(), randomForestJob);
  }


  void defaultExtremelyRandomTrees() {
    Algo algo = Algo.DRF;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.ModelBuild);
    if (work == null) return;

    DRFParameters drfParameters = new DRFParameters();
    setCommonModelBuilderParams(drfParameters);
    drfParameters._histogram_type = SharedTreeParameters.HistogramType.Random;
    drfParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();

    Job randomForestJob = trainModel(modelKey("XRT"), work, drfParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "Extremely Randomized Trees (XRT) Random Forest build", work, this.job(), randomForestJob);
  }


  /**
   * Build Arno's magical 5 default GBMs.
   */
  void defaultGBMs() {
    Algo algo = Algo.GBM;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.ModelBuild);
    if (work == null) return;

    Job gbmJob;

    GBMParameters gbmParameters = new GBMParameters();
    setCommonModelBuilderParams(gbmParameters);
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeParameters.HistogramType.AUTO;

    gbmParameters._ntrees = 1000;
    gbmParameters._sample_rate = 0.8;
    gbmParameters._col_sample_rate = 0.8;
    gbmParameters._col_sample_rate_per_tree = 0.8;

    // Default 1:
    gbmParameters._max_depth = 6;
    gbmParameters._min_rows = 1;

    gbmJob = trainModel(null, work, gbmParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM 1", work, this.job(), gbmJob);

    // Default 2:
    gbmParameters._max_depth = 7;
    gbmParameters._min_rows = 10;

    gbmJob = trainModel(null, work, gbmParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM 2", work, this.job(), gbmJob);

    // Default 3:
    gbmParameters._max_depth = 8;
    gbmParameters._min_rows = 10;

    gbmJob = trainModel(null, work, gbmParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM 3", work, this.job(), gbmJob);

    // Default 4:
    gbmParameters._max_depth = 10;
    gbmParameters._min_rows = 10;

    gbmJob = trainModel(null, work, gbmParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM 4", work, this.job(), gbmJob);

    // Default 5:
    gbmParameters._max_depth = 15;
    gbmParameters._min_rows = 100;

    gbmJob = trainModel(null, work, gbmParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM 5", work, this.job(), gbmJob);
  }


  void defaultDeepLearning() {
    Algo algo = Algo.DeepLearning;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.ModelBuild);
    if (work == null) return;

    DeepLearningParameters deepLearningParameters = new DeepLearningParameters();
    setCommonModelBuilderParams(deepLearningParameters);
    deepLearningParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();
    deepLearningParameters._hidden = new int[]{ 10, 10, 10 };

    Job deepLearningJob = trainModel(null, work, deepLearningParameters);
    pollAndUpdateProgress(Stage.ModelTraining, "Default Deep Learning build", work, this.job(), deepLearningJob);
  }


  void defaultSearchGLM(Key<Grid> gridKey) {
    Algo algo = Algo.GLM;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    GLMParameters glmParameters = new GLMParameters();
    setCommonModelBuilderParams(glmParameters);
    glmParameters._lambda_search = true;
    glmParameters._family =
            getResponseColumn().isBinary() && !(getResponseColumn().isNumeric()) ? GLMParameters.Family.binomial
            : getResponseColumn().isCategorical() ? GLMParameters.Family.multinomial
            : GLMParameters.Family.gaussian;  // TODO: other continuous distributions!

    Map<String, Object[]> searchParams = new HashMap<>();
    glmParameters._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};  // Note: standard GLM parameter is an array; don't use searchParams!
    // NOTE: removed MissingValuesHandling.Skip for now because it's crashing.  See https://0xdata.atlassian.net/browse/PUBDEV-4974
    searchParams.put("_missing_values_handling", new DeepLearningParameters.MissingValuesHandling[] {DeepLearningParameters.MissingValuesHandling.MeanImputation /* , DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip */});

    Job<Grid> glmJob = hyperparameterSearch(gridKey, work, glmParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, "GLM hyperparameter search", work, this.job(), glmJob);
  }

  void defaultSearchGBM(Key<Grid> gridKey) {
    Algo algo = Algo.GBM;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    GBMParameters gbmParameters = new GBMParameters();
    setCommonModelBuilderParams(gbmParameters);
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeParameters.HistogramType.AUTO;

    Map<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("_ntrees", new Integer[]{10000});
    searchParams.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
    searchParams.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
    searchParams.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
    searchParams.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
    searchParams.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0});
    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0});
    searchParams.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

    Job<Grid> gbmJob = hyperparameterSearch(gridKey, work, gbmParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, "GBM hyperparameter search", work, this.job(), gbmJob);
  }

  void defaultSearchDL1(Key<Grid> gridKey) {
    Algo algo = Algo.DeepLearning;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    DeepLearningParameters dlParameters = new DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);
    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = DeepLearningParameters.Activation.RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });
    // unique:
    searchParams.put("_hidden", new Integer[][] { {50}, {200}, {500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0 }, { 0.1 }, { 0.2 }, { 0.3 }, { 0.4 }, { 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch(gridKey, work, dlParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 1", work, this.job(), dlJob);
  }

  void defaultSearchDL2(Key<Grid> gridKey) {
    Algo algo = Algo.DeepLearning;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    DeepLearningParameters dlParameters = new DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);
    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = DeepLearningParameters.Activation.RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });
    // unique:
    searchParams.put("_hidden", new Integer[][] { {50, 50}, {200, 200}, {500, 500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0, 0.0 }, { 0.1, 0.1 }, { 0.2, 0.2 }, { 0.3, 0.3 }, { 0.4, 0.4 }, { 0.5, 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch(gridKey, work, dlParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 2", work, this.job(), dlJob);
  }

  void defaultSearchDL3(Key<Grid> gridKey) {
    Algo algo = Algo.DeepLearning;
    WorkAllocations.Work work = workAllocations.getAllocation(algo, JobType.HyperparamSearch);
    if (work == null) return;

    DeepLearningParameters dlParameters = new DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);
    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = DeepLearningParameters.Activation.RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });
    // unique:
    searchParams.put("_hidden", new Integer[][] { {50, 50, 50}, {200, 200, 200}, {500, 500, 500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0, 0.0, 0.0 }, { 0.1, 0.1, 0.1 }, { 0.2, 0.2, 0.2 }, { 0.3, 0.3, 0.3 }, { 0.4, 0.4, 0.4 }, { 0.5, 0.5, 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch(gridKey, work, dlParameters, searchParams);
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 3", work, this.job(), dlJob);
  }

  Job<StackedEnsembleModel> stack(String modelName, Key<Model>[]... modelKeyArrays) {
    WorkAllocations.Work work = workAllocations.getAllocation(Algo.StackedEnsemble, JobType.ModelBuild);
    if (work == null) return null;

    List<Key<Model>> allModelKeys = new ArrayList<>();
    for (Key<Model>[] modelKeyArray : modelKeyArrays)
      allModelKeys.addAll(Arrays.asList(modelKeyArray));
    // Set up Stacked Ensemble
    StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleParameters();
    stackedEnsembleParameters._base_models = allModelKeys.toArray(new Key[0]);
    stackedEnsembleParameters._valid = (getValidationFrame() == null ? null : getValidationFrame()._key);
    stackedEnsembleParameters._keep_levelone_frame = true; //TODO Why is this true? Can be optionally turned off
    // Add cross-validation args
    if (buildSpec.input_spec.fold_column != null) {
      stackedEnsembleParameters._metalearner_fold_column = buildSpec.input_spec.fold_column;
      stackedEnsembleParameters._metalearner_nfolds = 0;  //if fold_column is used, set nfolds to 0 (default)
    } else {
      stackedEnsembleParameters._metalearner_nfolds = buildSpec.build_control.nfolds;
    }
    stackedEnsembleParameters.initMetalearnerParams();
    stackedEnsembleParameters._metalearner_parameters._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
    stackedEnsembleParameters._metalearner_parameters._keep_cross_validation_predictions = buildSpec.build_control.keep_cross_validation_predictions;

    Key modelKey = modelKey(modelName, false);
    return trainModel(modelKey, work, stackedEnsembleParameters, true);
  }

  public void learn() {
    userFeedback.info(Stage.Workflow, "AutoML build started: " + fullTimestampFormat.format(new Date()));

    ///////////////////////////////////////////////////////////
    // gather initial frame metadata and guess the problem type
    ///////////////////////////////////////////////////////////
//    Disabling metadata collection for now as there is no use for it...
//    // TODO: Nishant says sometimes frameMetadata is null, so maybe we need to wait for it?
//    // null FrameMetadata arises when delete() is called without waiting for start() to finish.
//    frameMetadata = new FrameMetadata(userFeedback, trainingFrame,
//            trainingFrame.find(buildSpec.input_spec.response_column),
//            trainingFrame._key.toString()).computeFrameMetaPass1();
//
//    HashMap<String, Object> frameMeta = FrameMetadata.makeEmptyFrameMeta();
//    frameMetadata.fillSimpleMeta(frameMeta);
//    giveDatasetFeedback(trainingFrame, userFeedback, frameMeta);
//
//    job.update(20, "Computed dataset metadata");

//    isClassification = frameMetadata.isClassification();


    ///////////////////////////////////////////////////////////
    // build a fast RF with default settings...
    ///////////////////////////////////////////////////////////
    defaultRandomForest();

    ///////////////////////////////////////////////////////////
    // ... and another with "XRT" / extratrees settings
    ///////////////////////////////////////////////////////////
    defaultExtremelyRandomTrees();

    ///////////////////////////////////////////////////////////
    // build GLMs with the default search parameters
    ///////////////////////////////////////////////////////////
    defaultSearchGLM(null);

    ///////////////////////////////////////////////////////////
    // build default XGBoosts
    ///////////////////////////////////////////////////////////

//    defaultXGBoosts(true);

    defaultXGBoosts(false);

    ///////////////////////////////////////////////////////////
    // build five GBMs with Arno's default settings, using 1-grid
    // Cartesian searches into the same grid object as the search
    // below.
    ///////////////////////////////////////////////////////////
    defaultGBMs();

    ///////////////////////////////////////////////////////////
    // build a fast DL model with almost default settings...
    ///////////////////////////////////////////////////////////
    defaultDeepLearning();


    ///////////////////////////////////////////////////////////
    // so some XGBoost hyperparameter search
    ///////////////////////////////////////////////////////////

//    defaultSearchXGBoost(null, true);

    defaultSearchXGBoost(null, false);

    ///////////////////////////////////////////////////////////
    // build GBMs with the default search parameters
    ///////////////////////////////////////////////////////////
    defaultSearchGBM(null);

    //
    // Build DL models
    //
    Key<Grid> dlGridKey = gridKey(Algo.DeepLearning.name());
    ///////////////////////////////////////////////////////////
    // build DL models with default search parameter set 1
    ///////////////////////////////////////////////////////////
    defaultSearchDL1(dlGridKey);

    ///////////////////////////////////////////////////////////
    // build DL models with default search parameter set 2
    ///////////////////////////////////////////////////////////
    defaultSearchDL2(dlGridKey);

    ///////////////////////////////////////////////////////////
    // build DL models with default search parameter set 3
    ///////////////////////////////////////////////////////////
    defaultSearchDL3(dlGridKey);

    ///////////////////////////////////////////////////////////
    // (optionally) build StackedEnsemble
    ///////////////////////////////////////////////////////////
    Model[] allModels = leaderboard().getModels();

    WorkAllocations.Work seWork = workAllocations.getAllocation(Algo.StackedEnsemble, JobType.ModelBuild);
    if (seWork == null) {
      this.job.update(0, "StackedEnsemble builds skipped");
      userFeedback.info(Stage.ModelTraining, "StackedEnsemble builds skipped due to the exclude_algos option.");
    } else if (allModels.length == 0) {
      this.job.update(seWork.consumeAll(), "No models built; StackedEnsemble builds skipped");
      userFeedback.info(Stage.ModelTraining, "No models were built, due to timeouts or the exclude_algos option. StackedEnsemble builds skipped.");
    } else if (allModels.length == 1) {
      this.job.update(seWork.consumeAll(), "One model built; StackedEnsemble builds skipped");
      userFeedback.info(Stage.ModelTraining, "StackedEnsemble builds skipped since there is only one model built");
    } else if (buildSpec.build_control.nfolds == 0) {
        this.job.update(seWork.consumeAll(), "Cross-validation disabled by the user; StackedEnsemble build skipped");
        userFeedback.info(Stage.ModelTraining,"Cross-validation disabled by the user; StackedEnsemble build skipped");
    } else {
      ///////////////////////////////////////////////////////////
      // stack all models
      ///////////////////////////////////////////////////////////

      // Also stack models from other AutoML runs, by using the Leaderboard! (but don't stack stacks)
      int nonEnsembleCount = 0;
      for (Model aModel : allModels)
        if (!(aModel instanceof StackedEnsembleModel))
          nonEnsembleCount++;

      Key<Model>[] notEnsembles = new Key[nonEnsembleCount];
      int notEnsembleIndex = 0;
      for (Model aModel : allModels)
        if (!(aModel instanceof StackedEnsembleModel))
          notEnsembles[notEnsembleIndex++] = aModel._key;

      Job<StackedEnsembleModel> ensembleJob = stack("StackedEnsemble_AllModels", notEnsembles);
      pollAndUpdateProgress(Stage.ModelTraining, "StackedEnsemble build using all AutoML models", seWork, this.job(), ensembleJob, true);

      // Set aside List<Model> for best models per model type. Meaning best GLM, GBM, DRF, XRT, and DL (5 models).
      // This will give another ensemble that is smaller than the original which takes all models into consideration.
      List<Model> bestModelsOfEachType = new ArrayList<>();
      Set<String> typesOfGatheredModels = new HashSet<>();

      for (Model aModel : allModels) {
        String type = getModelType(aModel);
        if (aModel instanceof StackedEnsembleModel || typesOfGatheredModels.contains(type)) continue;
        typesOfGatheredModels.add(type);
        bestModelsOfEachType.add(aModel);
      }

      Key<Model>[] bestModelKeys = new Key[bestModelsOfEachType.size()];
      for (int i = 0; i < bestModelsOfEachType.size(); i++)
        bestModelKeys[i] = bestModelsOfEachType.get(i)._key;

      Job<StackedEnsembleModel> bestEnsembleJob = stack("StackedEnsemble_BestOfFamily", bestModelKeys);
      pollAndUpdateProgress(Stage.ModelTraining, "StackedEnsemble build using top model from each algorithm type", seWork, this.job(), bestEnsembleJob, true);
    }
    userFeedback.info(Stage.Workflow, "AutoML: build done; built " + modelCount + " models");
    Log.info(userFeedback.toString("User Feedback for AutoML Run " + this._key + ":"));
    for (UserFeedbackEvent event : userFeedback.feedbackEvents)
      Log.info(event);

    if (0 < this.leaderboard().getModelKeys().length) {

      //TODO Below should really be a parameter, but needs more thought...
      // We should not spend time computing train/valid leaderboards until we are ready to expose them to the user
      // Commenting this section out for now
      /*
      // Use a throwaway AutoML instance so the "New leader" message doesn't pollute our feedback
      AutoML dummyAutoML = new AutoML();
      UserFeedback dummyUF = new UserFeedback(dummyAutoML);
      dummyAutoML.userFeedback = dummyUF;

      Leaderboard trainingLeaderboard = Leaderboard.getOrMakeLeaderboard(projectName() + "_training", dummyUF, this.trainingFrame);
      trainingLeaderboard.addModels(this.leaderboard().getModelKeys());
      Log.info(trainingLeaderboard.toTwoDimTable("TRAINING FRAME Leaderboard for project " + projectName(), true).toString());
      Log.info();

      // Use a throwawayUserFeedback instance so the "New leader" message doesn't pollute our feedback
      Leaderboard validationLeaderboard = Leaderboard.getOrMakeLeaderboard(projectName() + "_validation", dummyUF, this.validationFrame);
      validationLeaderboard.addModels(this.leaderboard().getModelKeys());
      Log.info(validationLeaderboard.toTwoDimTable("VALIDATION FRAME Leaderboard for project " + projectName(), true).toString());
      Log.info();
      */

      Log.info(leaderboard().toTwoDimTable("Leaderboard for project " + projectName(), true).toString());
    }

    possiblyVerifyImmutability();

    if (!buildSpec.build_control.keep_cross_validation_predictions) {
      cleanUpModelsCVPreds();
    }

    // gather more data? build more models? start applying transforms? what next ...?
    stop();
  } // end of learn()

  /**
   * Instantiate an AutoML object and start it running.  Progress can be tracked via its job().
   *
   * @param buildSpec
   * @return
   */
  public static AutoML startAutoML(AutoMLBuildSpec buildSpec) {
    Date startTime = new Date();  // this is the one and only startTime for this run

    synchronized (AutoML.class) {
      // protect against two runs whose startTime is the same second
      if (lastStartTime != null) {
        while (lastStartTime.getYear() == startTime.getYear() &&
                lastStartTime.getMonth() == startTime.getMonth() &&
                lastStartTime.getDate() == startTime.getDate() &&
                lastStartTime.getHours() == startTime.getHours() &&
                lastStartTime.getMinutes() == startTime.getMinutes() &&
                lastStartTime.getSeconds() == startTime.getSeconds())
          startTime = new Date();
      }
      lastStartTime = startTime;
    }

    String keyString = buildSpec.build_control.project_name;
    AutoML aml = AutoML.makeAutoML(Key.<AutoML>make(keyString), startTime, buildSpec);

    DKV.put(aml);
    startAutoML(aml);
    return aml;
  }

  /**
   * Takes in an AutoML instance and starts running it. Progress can be tracked via its job().
   * @param aml
   * @return
     */
  public static void startAutoML(AutoML aml) {
    // Currently AutoML can only run one job at a time
    if (aml.job == null || !aml.job.isRunning()) {
      H2OJob j = new H2OJob(aml, aml._key, aml.timeRemainingMs());
      aml.job = j._job;
      aml.planWork();
      j.start(aml.workAllocations.remainingWork());
      DKV.put(aml);
    }
  }

  /**
   * Holds until AutoML's job is completed, if a job exists.
   */
  public void get() {
    if (job != null) job.get();
  }


  /**
   * Delete the AutoML-related objects, but leave the grids and models that it built.
   */
  @Override
  protected Futures remove_impl(Futures fs) {
    Frame.deleteTempFrameAndItsNonSharedVecs(trainingFrame, origTrainingFrame);
    leaderboard.remove(fs);
    userFeedback.remove(fs);
    return super.remove_impl(fs);
  }

  /**
   * Same as delete() but also deletes all Objects made from this instance.
   */
  void deleteWithChildren() {
    leaderboard.deleteWithChildren();

    for (Key<Grid> gridKey : gridKeys)
      gridKey.remove();

    // If the Frame was made here (e.g. buildspec contained a path, then it will be deleted
    if (buildSpec.input_spec.training_frame == null) {
      origTrainingFrame.delete();
    }
    if (buildSpec.input_spec.validation_frame == null && validationFrame != null) {
      validationFrame.delete();
    }
    if (buildSpec.input_spec.leaderboard_frame == null && leaderboardFrame != null) {
      leaderboardFrame.delete();
    }
    delete();
  }

  public Job job() {
    if (null == this.job) return null;
    return DKV.getGet(this.job._key);
  }

  public Leaderboard leaderboard() { return (leaderboard == null ? null : leaderboard._key.get()); }
  public Model leader() { return (leaderboard() == null ? null : leaderboard().getLeader()); }

  public UserFeedback userFeedback() { return userFeedback == null ? null : userFeedback._key.get(); }

  public String projectName() {
    return buildSpec == null ? null : buildSpec.project();
  }

  // If we have multiple AutoML engines running on the same
  // project they will be updating the Leaderboard concurrently,
  // so always use leaderboard() instead of the raw field, to get
  // it from the DKV.
  //
  // Also, the leaderboard will reject duplicate models, so use
  // the difference in Leaderboard length here:
  private void addModels(final Key<Model>[] newModels) {
    int before = leaderboard().getModelCount();
    leaderboard().addModels(newModels);
    int after = leaderboard().getModelCount();
    modelCount.addAndGet(after - before);
  }

  private void addModel(final Model newModel) {
    int before = leaderboard().getModelCount();
    leaderboard().addModel(newModel);
    int after = leaderboard().getModelCount();
    modelCount.addAndGet(after - before);
  }

  private String getSortMetric() {
    //ensures that the sort metric is always updated according to the defaults set by leaderboard
    Leaderboard leaderboard = leaderboard();
    return leaderboard == null ? null : leaderboard.sort_metric;
  }

  private static StoppingMetric metricValueOf(String name) {
    if (name == null) return StoppingMetric.AUTO;
    switch (name) {
      case "mean_residual_deviance": return StoppingMetric.deviance;
      default:
        String[] attempts = { name, name.toUpperCase(), name.toLowerCase() };
        for (String attempt : attempts) {
          try {
            return StoppingMetric.valueOf(attempt);
          } catch (IllegalArgumentException ignored) { }
        }
        return StoppingMetric.AUTO;
    }
  }

  // satisfy typing for job return type...
  public static class AutoMLKeyV3 extends KeyV3<Iced, AutoMLKeyV3, AutoML> {
    public AutoMLKeyV3() { }

    public AutoMLKeyV3(Key<AutoML> key) {
      super(key);
    }
  }

  @Override
  public Class<AutoMLKeyV3> makeSchema() {
    return AutoMLKeyV3.class;
  }

  private class AutoMLDoneException extends H2OAbstractRuntimeException {
    public AutoMLDoneException() {
      this("done", "done");
    }

    public AutoMLDoneException(String msg, String dev_msg) {
      super(msg, dev_msg, new IcedHashMapGeneric.IcedHashMapStringObject());
    }
  }

  private boolean possiblyVerifyImmutability() {
    boolean warning = false;

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      userFeedback.debug(Stage.Workflow, "Verifying training frame immutability. . .");

      Vec[] vecsRightNow = origTrainingFrame.vecs();
      String[] namesRightNow = origTrainingFrame.names();

      if (originalTrainingFrameVecs.length != vecsRightNow.length) {
        Log.warn("Training frame vec count has changed from: " +
                originalTrainingFrameVecs.length + " to: " + vecsRightNow.length);
        warning = true;
      }
      if (originalTrainingFrameNames.length != namesRightNow.length) {
        Log.warn("Training frame vec count has changed from: " +
                originalTrainingFrameNames.length + " to: " + namesRightNow.length);
        warning = true;
      }

      for (int i = 0; i < originalTrainingFrameVecs.length; i++) {
        if (!originalTrainingFrameVecs[i].equals(vecsRightNow[i])) {
          Log.warn("Training frame vec number " + i + " has changed keys.  Was: " +
                  originalTrainingFrameVecs[i] + " , now: " + vecsRightNow[i]);
          warning = true;
        }
        if (!originalTrainingFrameNames[i].equals(namesRightNow[i])) {
          Log.warn("Training frame vec number " + i + " has changed names.  Was: " +
                  originalTrainingFrameNames[i] + " , now: " + namesRightNow[i]);
          warning = true;
        }
        if (originalTrainingFrameChecksums[i] != vecsRightNow[i].checksum()) {
          Log.warn("Training frame vec number " + i + " has changed checksum.  Was: " +
                  originalTrainingFrameChecksums[i] + " , now: " + vecsRightNow[i].checksum());
          warning = true;
        }
      }

      if (warning)
        userFeedback.warn(Stage.Workflow, "Training frame was mutated!  This indicates a bug in the AutoML software.");
      else
        userFeedback.debug(Stage.Workflow, "Training frame was not mutated (as expected).");

    } else {
      userFeedback.debug(Stage.Workflow, "Not verifying training frame immutability. . .  This is turned off for efficiency.");
    }

    return warning;
  }

  private void giveDatasetFeedback(Frame frame, UserFeedback userFeedback, HashMap<String, Object> frameMeta) {
    userFeedback.info(Stage.FeatureAnalysis, "Metadata for Frame: " + frame._key.toString());
    for (Map.Entry<String, Object> entry : frameMeta.entrySet()) {
      if (entry.getKey().startsWith("Dummy"))
        continue;
      Object val = entry.getValue();
      if (val instanceof Double || val instanceof Float)
        userFeedback.info(Stage.FeatureAnalysis, entry.getKey() + ": " + String.format("%.6f", val));
      else
        userFeedback.info(Stage.FeatureAnalysis, entry.getKey() + ": " + entry.getValue());
    }
  }

  private String getModelType(Model m) {
    return m._key.toString().startsWith("XRT_") ? "XRT" : m._parms.algoName();
  }

  private void cleanUpModelsCVPreds() {
    Log.info("Cleaning up all CV Predictions for AutoML");
    for (Model model : leaderboard().getModels()) {
        model.deleteCrossValidationPreds();
    }
  }

}
