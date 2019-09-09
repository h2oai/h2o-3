package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import ai.h2o.automl.WorkAllocations.JobType;
import hex.Model;
import hex.grid.Grid;
import hex.splitframe.ShuffleSplitFrame;
import water.*;
import water.automl.api.schemas3.AutoMLV99;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMap;
import water.util.Countdown;
import water.util.Log;
import water.util.PrettyPrint;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria.AUTO_STOPPING_TOLERANCE;


/**
 * H2O AutoML
 *
 * AutoML  is used for automating the machine learning workflow, which includes automatic training and
 * tuning of many models within a user-specified time-limit. Stacked Ensembles will be automatically
 * trained on collections of individual models to produce highly predictive ensemble models which, in most cases,
 * will be the top performing models in the AutoML Leaderboard.
 */
public final class AutoML extends Lockable<AutoML> implements TimedH2ORunnable {

  private final static boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with
  private final static SimpleDateFormat timestampFormatForKeys = new SimpleDateFormat("yyyyMMdd_HHmmss");

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
      H2OJob j = new H2OJob(aml, aml._key, aml.runCountdown.remainingTime());
      aml.job = j._job;
      j.start(aml.workAllocations.remainingWork());
      DKV.put(aml);
    }
  }

  public static AutoML makeAutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {

    return new AutoML(key, startTime, buildSpec);
  }

  @Override
  public Class<AutoMLV99.AutoMLKeyV3> makeSchema() {
    return AutoMLV99.AutoMLKeyV3.class;
  }

  private AutoMLBuildSpec buildSpec;     // all parameters for doing this AutoML build
  private Frame origTrainingFrame;       // untouched original training frame

  public AutoMLBuildSpec getBuildSpec() {
    return buildSpec;
  }

  public Frame getTrainingFrame() { return trainingFrame; }
  public Frame getValidationFrame() { return validationFrame; }
  public Frame getBlendingFrame() { return blendingFrame; }
  public Frame getLeaderboardFrame() { return leaderboardFrame; }

  public Vec getResponseColumn() { return responseColumn; }
  public Vec getFoldColumn() { return foldColumn; }
  public Vec getWeightsColumn() { return weightsColumn; }

  Frame trainingFrame;    // required training frame: can add and remove Vecs, but not mutate Vec data in place
  Frame validationFrame;  // optional validation frame; the training_frame is split automagically if it's not specified
  Frame blendingFrame;
  Frame leaderboardFrame; // optional test frame used for leaderboard scoring; if not specified, leaderboard will use xval metrics

  Vec responseColumn;
  Vec foldColumn;
  Vec weightsColumn;

  Key<Grid> gridKeys[] = new Key[0];  // Grid key for the GridSearches

  Date startTime;
  static Date lastStartTime; // protect against two runs with the same second in the timestamp; be careful about races
  Countdown runCountdown;
  Job job;                  // the Job object for the build of this AutoML.
  WorkAllocations workAllocations;

  private TrainingStepsRegistry trainingStepsRegistry;
  private TrainingStepsExecutor trainingStepsExecutor;
  private Leaderboard leaderboard;
  private EventLog eventLog;

  // check that we haven't messed up the original Frame
  private Vec[] originalTrainingFrameVecs;
  private String[] originalTrainingFrameNames;
  private long[] originalTrainingFrameChecksums;

  public AutoML() {
    super(null);
  }

  public AutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {
    super(key);
    this.startTime = startTime;
    this.buildSpec = buildSpec;
    runCountdown = Countdown.fromSeconds(buildSpec.build_control.stopping_criteria.max_runtime_secs());

    try {
      eventLog = EventLog.make(this._key);
      eventLog().info(Stage.Workflow, "Project: " + projectName());
      eventLog().info(Stage.Workflow, "AutoML job created: " + EventLogEntry.dateTimeFormat.format(this.startTime))
              .setNamedValue("creation_epoch", this.startTime, EventLogEntry.epochFormat);

      workAllocations = planWork();
      
      handleCVParameters(buildSpec);

      handleReproducibilityParameters(buildSpec);

      handleDatafileParameters(buildSpec);

      handleEarlyStoppingParameters(buildSpec);

      initLeaderboard(buildSpec);

      trainingStepsRegistry = new TrainingStepsRegistry(this);
      trainingStepsExecutor = new TrainingStepsExecutor(leaderboard, eventLog, runCountdown);
    } catch (Exception e) {
      delete(); //cleanup potentially leaked keys
      throw e;
    }
  }

  private void initLeaderboard(AutoMLBuildSpec buildSpec) {
    String sort_metric = buildSpec.input_spec.sort_metric == null ? null : buildSpec.input_spec.sort_metric.toLowerCase();
    leaderboard = Leaderboard.getOrMake(projectName(), eventLog, this.leaderboardFrame, sort_metric);
  }

  private void handleReproducibilityParameters(AutoMLBuildSpec buildSpec) {
    eventLog().info(Stage.Workflow, "Build control seed: " + buildSpec.build_control.stopping_criteria.seed() +
            (buildSpec.build_control.stopping_criteria.seed() == -1 ? " (random)" : ""));
  }

  private void handleEarlyStoppingParameters(AutoMLBuildSpec buildSpec) {
    if (buildSpec.build_control.stopping_criteria.stopping_tolerance() == AUTO_STOPPING_TOLERANCE) {
      buildSpec.build_control.stopping_criteria.set_default_stopping_tolerance_for_frame(this.trainingFrame);
      eventLog().info(Stage.Workflow, "Setting stopping tolerance adaptively based on the training frame: " +
              buildSpec.build_control.stopping_criteria.stopping_tolerance());
    } else {
      eventLog().info(Stage.Workflow, "Stopping tolerance set by the user: " + buildSpec.build_control.stopping_criteria.stopping_tolerance());
      double default_tolerance = AutoMLBuildSpec.AutoMLStoppingCriteria.default_stopping_tolerance_for_frame(this.trainingFrame);
      if (buildSpec.build_control.stopping_criteria.stopping_tolerance() < 0.7 * default_tolerance){
        eventLog().warn(Stage.Workflow, "Stopping tolerance set by the user is < 70% of the recommended default of " + default_tolerance + ", so models may take a long time to converge or may not converge at all.");
      }
    }
  }

  private void handleCVParameters(AutoMLBuildSpec buildSpec) {
    if (null != buildSpec.input_spec.fold_column) {
      eventLog().warn(Stage.Workflow, "Custom fold column, " + buildSpec.input_spec.fold_column + ", will be used. nfolds value will be ignored.");
      buildSpec.build_control.nfolds = 0; //reset nfolds to Model default
    }
  }


  WorkAllocations planWork() {
    if (buildSpec.build_models.exclude_algos != null && buildSpec.build_models.include_algos != null) {
      throw new  H2OIllegalArgumentException("Parameters `exclude_algos` and `include_algos` are mutually exclusive: please use only one of them if necessary.");
    }

    Set<Algo> skippedAlgos = new HashSet<>();
    if (buildSpec.build_models.exclude_algos != null) {
      skippedAlgos.addAll(Arrays.asList(buildSpec.build_models.exclude_algos));
    } else if (buildSpec.build_models.include_algos != null) {
      skippedAlgos.addAll(Arrays.asList(Algo.values()));
      skippedAlgos.removeAll(Arrays.asList(buildSpec.build_models.include_algos));
    }

    for (Algo algo : Algo.values()) {
      if (!skippedAlgos.contains(algo) && !algo.enabled()) {
        eventLog.warn(Stage.ModelTraining, "AutoML: "+algo.name()+" is not available; skipping it.");
        skippedAlgos.add(algo);
      }
    }

    WorkAllocations workAllocations = new WorkAllocations();
    // todo: merge this with steps
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

    for (Algo skippedAlgo : skippedAlgos) {
      eventLog().info(Stage.ModelTraining, "Disabling Algo: "+skippedAlgo+" as requested by the user.");
      workAllocations.remove(skippedAlgo);
    }

    return workAllocations;
  }

  @Override
  public void run() {
    runCountdown.start();
    trainingStepsExecutor.start();
    eventLog().info(Stage.Workflow, "AutoML build started: " + EventLogEntry.dateTimeFormat.format(runCountdown.start_time()))
            .setNamedValue("start_epoch", runCountdown.start_time(), EventLogEntry.epochFormat);
    learn();
    stop();
  }

  @Override
  public void stop() {
    if (null == trainingStepsExecutor) return; // already stopped
    trainingStepsExecutor.stop();
    runCountdown.stop();
    eventLog().info(Stage.Workflow, "AutoML build stopped: " + EventLogEntry.dateTimeFormat.format(runCountdown.stop_time()))
            .setNamedValue("stop_epoch", runCountdown.stop_time(), EventLogEntry.epochFormat);
    eventLog().info(Stage.Workflow, "AutoML build done: built " + trainingStepsExecutor.modelCount() + " models");
    eventLog().info(Stage.Workflow, "AutoML duration: "+ PrettyPrint.msecs(runCountdown.duration(), true))
            .setNamedValue("duration_secs", Math.round(runCountdown.duration() / 1000.));

    Log.info(eventLog().toString("Event Log for AutoML Run " + this._key + ":"));
    for (EventLogEntry event : eventLog()._events)
      Log.info(event);

    if (0 < this.leaderboard().getModelKeys().length) {
      Log.info(leaderboard().toTwoDimTable("Leaderboard for project " + projectName(), true).toString());
    }

    possiblyVerifyImmutability();
    if (!buildSpec.build_control.keep_cross_validation_predictions) {
      cleanUpModelsCVPreds();
    }
  }

  /**
   * Holds until AutoML's job is completed, if a job exists.
   */
  public void get() {
    if (job != null) job.get();
  }

  public Job job() {
    if (null == this.job) return null;
    return DKV.getGet(this.job._key);
  }

  public Model leader() {
    return leaderboard() == null ? null : leaderboard.getLeader();
  }

  public Leaderboard leaderboard() {
    return leaderboard == null ? null : (leaderboard = leaderboard._key.get());
  }

  public EventLog eventLog() {
    return eventLog == null ? null : (eventLog = eventLog._key.get());
  }

  public String projectName() {
    return buildSpec == null ? null : buildSpec.project();
  }

  public long timeRemainingMs() {
    return runCountdown.remainingTime();
  }

  public int remainingModels() {
    if (buildSpec.build_control.stopping_criteria.max_models() == 0)
      return Integer.MAX_VALUE;
    return buildSpec.build_control.stopping_criteria.max_models() - trainingStepsExecutor.modelCount();
  }

  @Override
  public boolean keepRunning() {
    return !runCountdown.timedOut() && remainingModels() > 0;
  }

  boolean isCVEnabled() {
    return this.buildSpec.build_control.nfolds != 0 || this.buildSpec.input_spec.fold_column != null;
  }


  //*****************  Data Preparation Section  *****************//

  private void optionallySplitTrainingDataset() {
    // If no cross-validation and validation or leaderboard frame are missing,
    // then we need to create one out of the original training set.
    if (!isCVEnabled()) {
      double[] splitRatios = null;
      if (null == this.validationFrame && null == this.leaderboardFrame) {
        splitRatios = new double[]{ 0.8, 0.1, 0.1 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and none of validation frame and leaderboard frame were provided, " +
                "automatically split the training data into training, validation and leaderboard frames in the ratio 80/10/10");
      } else if (null == this.validationFrame) {
        splitRatios = new double[]{ 0.9, 0.1, 0 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and no validation frame was provided, " +
                "automatically split the training data into training and validation frames in the ratio 90/10");
      } else if (null == this.leaderboardFrame) {
        splitRatios = new double[]{ 0.9, 0, 0.1 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and no leaderboard frame was provided, " +
                "automatically split the training data into training and leaderboard frames in the ratio 90/10");
      }
      if (splitRatios != null) {
        Key[] keys = new Key[] {
            Key.make("automl_training_"+origTrainingFrame._key),
            Key.make("automl_validation_"+origTrainingFrame._key),
            Key.make("automl_leaderboard_"+origTrainingFrame._key),
        };
        Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(
            origTrainingFrame,
            keys,
            splitRatios,
            buildSpec.build_control.stopping_criteria.seed()
        );
        this.trainingFrame = splits[0];

        if (this.validationFrame == null && splits[1].numRows() > 0) {
          this.validationFrame = splits[1];
        } else {
          splits[1].delete();
        }

        if (this.leaderboardFrame == null && splits[2].numRows() > 0) {
          this.leaderboardFrame = splits[2];
        } else {
          splits[2].delete();
        }
      }
    }
  }
  private void handleDatafileParameters(AutoMLBuildSpec buildSpec) {
    this.origTrainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
    this.validationFrame = DKV.getGet(buildSpec.input_spec.validation_frame);
    this.blendingFrame = DKV.getGet(buildSpec.input_spec.blending_frame);
    this.leaderboardFrame = DKV.getGet(buildSpec.input_spec.leaderboard_frame);

    if (null == this.origTrainingFrame)
      throw new H2OIllegalArgumentException("No training data has been specified, either as a path or a key.");

    Map<String, Frame> compatible_frames = new LinkedHashMap(){{
      put("training", origTrainingFrame);
      put("validation", validationFrame);
      put("blending", blendingFrame);
      put("leaderboard", leaderboardFrame);
    }};
    for (Map.Entry<String, Frame> entry : compatible_frames.entrySet()) {
      Frame frame = entry.getValue();
      if (frame != null && frame.find(buildSpec.input_spec.response_column) == -1) {
        throw new H2OIllegalArgumentException("Response column '"+buildSpec.input_spec.response_column+"' is not in the "+entry.getKey()+" frame.");
      }
    }

    if (buildSpec.input_spec.fold_column != null && this.origTrainingFrame.find(buildSpec.input_spec.fold_column) == -1) {
      throw new H2OIllegalArgumentException("Fold column '"+buildSpec.input_spec.fold_column+"' is not in the training frame.");
    }
    if (buildSpec.input_spec.weights_column != null && this.origTrainingFrame.find(buildSpec.input_spec.weights_column) == -1) {
      throw new H2OIllegalArgumentException("Weights column '"+buildSpec.input_spec.weights_column+"' is not in the training frame.");
    }

    optionallySplitTrainingDataset();

    if (null == this.trainingFrame) {
      // when nfolds>0, let trainingFrame be the original frame
      // but cloning to keep an internal ref just in case the original ref gets deleted from client side
      // (can occur in some corner cases with Python GC for example if frame get's out of scope during an AutoML rerun)
      this.trainingFrame = new Frame(origTrainingFrame);
      this.trainingFrame._key = Key.make("automl_training_" + origTrainingFrame._key);
      DKV.put(this.trainingFrame);
    }

    this.responseColumn = trainingFrame.vec(buildSpec.input_spec.response_column);
    this.foldColumn = trainingFrame.vec(buildSpec.input_spec.fold_column);
    this.weightsColumn = trainingFrame.vec(buildSpec.input_spec.weights_column);

    this.eventLog().info(Stage.DataImport,
        "training frame: "+this.trainingFrame.toString().replace("\n", " ")+" checksum: "+this.trainingFrame.checksum());
    if (null != this.validationFrame) {
      this.eventLog().info(Stage.DataImport,
          "validation frame: "+this.validationFrame.toString().replace("\n", " ")+" checksum: "+this.validationFrame.checksum());
    } else {
      this.eventLog().info(Stage.DataImport, "validation frame: NULL");
    }
    if (null != this.leaderboardFrame) {
      this.eventLog().info(Stage.DataImport,
          "leaderboard frame: "+this.leaderboardFrame.toString().replace("\n", " ")+" checksum: "+this.leaderboardFrame.checksum());
    } else {
      this.eventLog().info(Stage.DataImport, "leaderboard frame: NULL");
    }

    this.eventLog().info(Stage.DataImport, "response column: "+buildSpec.input_spec.response_column);
    this.eventLog().info(Stage.DataImport, "fold column: "+this.foldColumn);
    this.eventLog().info(Stage.DataImport, "weights column: "+this.weightsColumn);

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


  //*****************  Training Jobs  *****************//

  private void learn() {
    for (TrainingStep step : trainingStepsRegistry.getOrderedSteps()) {
        if (!exceededSearchLimits(step._description, step._ignoreConstraints)) {
          trainingStepsExecutor.submit(step, job());
        }
    }
  }


  // There are per (possibly concurrent) AutoML run.
  // All created keys for a run use the unique AutoML run timestamp, so we can't have name collisions.
  AtomicInteger individualModelsTrained = new AtomicInteger();
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

  Key<Model> modelKey(String algoName, boolean with_counter) {
    String counterStr = with_counter ? "_" + nextInstanceCounter(algoName, algoInstanceCounters) : "";
    return Key.make(algoName + counterStr + "_AutoML_" + timestampFormatForKeys.format(this.startTime));
  }

  Key<Grid> gridKey(String algoName, boolean with_counter) {
    String counterStr = with_counter ? "_" + nextInstanceCounter(algoName, gridInstanceCounters) : "";
    return Key.make(algoName + "_grid_" + counterStr + "_AutoML_" + timestampFormatForKeys.format(this.startTime));
  }

  void addGridKey(Key<Grid> gridKey) {
    gridKeys = Arrays.copyOf(gridKeys, gridKeys.length + 1);
    gridKeys[gridKeys.length - 1] = gridKey;
  }

  private boolean exceededSearchLimits(String modelDesc, boolean ignoreLimits) {
    if (job.stop_requested()) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: job cancelled; skipping "+modelDesc);
      return true;
    }

    if (!ignoreLimits && runCountdown.timedOut()) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: out of time; skipping "+modelDesc);
      return true;
    }

    if (!ignoreLimits && remainingModels() <= 0) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: hit the max_models limit; skipping "+modelDesc);
      return true;
    }
    return false;
  }

  //*****************  Clean Up + other utility functions *****************//

  /**
   * Delete the AutoML-related objects, but leave the grids and models that it built.
   */
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    Key<Job> jobKey = job == null ? null : job._key;
    Log.debug("Cleaning up AutoML "+jobKey);
    // If the Frame was made here (e.g. buildspec contained a path, then it will be deleted
    if (buildSpec.input_spec.training_frame == null && origTrainingFrame != null) {
      origTrainingFrame.delete(jobKey, fs, true);
    }
    if (buildSpec.input_spec.validation_frame == null && validationFrame != null) {
      validationFrame.delete(jobKey, fs, true);
    }
    if (buildSpec.input_spec.leaderboard_frame == null && leaderboardFrame != null) {
      leaderboardFrame.delete(jobKey, fs, true);
    }

    if (trainingFrame != null && origTrainingFrame != null)
      Frame.deleteTempFrameAndItsNonSharedVecs(trainingFrame, origTrainingFrame);
    if (leaderboard() != null) leaderboard().remove(fs, cascade);
    if (eventLog() != null) eventLog().remove(fs, cascade);

    // grid should be removed after leaderboard cleanup
    if (gridKeys != null)
      for (Key<Grid> gridKey : gridKeys) Keyed.remove(gridKey, fs, true);

    return super.remove_impl(fs, cascade);
  }

  private boolean possiblyVerifyImmutability() {
    boolean warning = false;

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      eventLog().debug(Stage.Workflow, "Verifying training frame immutability. . .");

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
        eventLog().warn(Stage.Workflow, "Training frame was mutated!  This indicates a bug in the AutoML software.");
      else
        eventLog().debug(Stage.Workflow, "Training frame was not mutated (as expected).");

    } else {
      eventLog().debug(Stage.Workflow, "Not verifying training frame immutability. . .  This is turned off for efficiency.");
    }

    return warning;
  }

  private void cleanUpModelsCVPreds() {
    Log.info("Cleaning up all CV Predictions for AutoML");
    for (Model model : leaderboard().getModels()) {
        model.deleteCrossValidationPreds();
    }
  }
}
