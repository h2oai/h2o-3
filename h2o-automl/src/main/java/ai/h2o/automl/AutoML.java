package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import ai.h2o.automl.StepDefinition.Alias;
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

  public static final Comparator<AutoML> byStartTime = Comparator.comparing(a -> a._startTime);
  public static final String keySeparator = "@@";

  private final static boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with
  private final static SimpleDateFormat timestampFormatForKeys = new SimpleDateFormat("yyyyMMdd_HHmmss");

  private static StepDefinition[] defaultModelingPlan = {
          new StepDefinition(Algo.XGBoost.name(), Alias.defaults),
          new StepDefinition(Algo.GLM.name(), Alias.defaults),
          new StepDefinition(Algo.DRF.name(), new String[]{ "def_1" }),
          new StepDefinition(Algo.GBM.name(), Alias.defaults),
          new StepDefinition(Algo.DeepLearning.name(), Alias.defaults),
          new StepDefinition(Algo.DRF.name(), new String[]{ "XRT" }),
          new StepDefinition(Algo.XGBoost.name(), Alias.grids),
          new StepDefinition(Algo.GBM.name(), Alias.grids),
          new StepDefinition(Algo.DeepLearning.name(), Alias.grids),
          new StepDefinition(Algo.StackedEnsemble.name(), Alias.defaults),
  };

  private static Date lastStartTime; // protect against two runs with the same second in the timestamp; be careful about races
  /**
   * Instantiate an AutoML object and start it running.  Progress can be tracked via its job().
   *
   * @param buildSpec
   * @return a new running AutoML instance.
   */
  public static AutoML startAutoML(AutoMLBuildSpec buildSpec) {
    Date startTime = new Date();  // this is the one and only startTime for this run

    synchronized (AutoML.class) {
      // protect against two runs whose startTime is the same second
      if (lastStartTime != null) {
        while (Math.abs(lastStartTime.getTime() - startTime.getTime()) < 1e3 ) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException ignored) {}
          startTime = new Date();
        }
      }
      lastStartTime = startTime;
    }

    // if user offers a different response column,
    //   the new models will be added to a new Leaderboard, without removing the previous one.
    // otherwise, the new models will be added to the existing leaderboard.
    Key<AutoML> key = Key.make(buildSpec.project()+keySeparator+buildSpec.input_spec.response_column);
    AutoML aml = new AutoML(key, startTime, buildSpec);
    startAutoML(aml);
    return aml;
  }

  /**
   * Takes in an AutoML instance and starts running it. Progress can be tracked via its job().
   * @param aml
   * @deprecated Prefer {@link #startAutoML(AutoMLBuildSpec)} instead.
   */
  public static void startAutoML(AutoML aml) {
    // Currently AutoML can only run one job at a time
    if (aml._job == null || !aml._job.isRunning()) {
      H2OJob<AutoML> j = new H2OJob<>(aml, aml._key, aml._runCountdown.remainingTime());
      aml._job = j._job;
      aml.eventLog().info(Stage.Workflow, "AutoML job created: " + EventLogEntry.dateTimeFormat.format(aml._startTime))
              .setNamedValue("creation_epoch", aml._startTime, EventLogEntry.epochFormat);
      j.start(aml._workAllocations.remainingWork());
      DKV.put(aml);
    }
  }

  /**
   * @deprecated : Please use {@link #AutoML(Key, Date, AutoMLBuildSpec)} constructor instead
   */
  @Deprecated
  public static AutoML makeAutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {
    return new AutoML(key, startTime, buildSpec);
  }

  @Override
  public Class<AutoMLV99.AutoMLKeyV3> makeSchema() {
    return AutoMLV99.AutoMLKeyV3.class;
  }

  private AutoMLBuildSpec _buildSpec;     // all parameters for doing this AutoML build
  private Frame _origTrainingFrame;       // untouched original training frame

  public AutoMLBuildSpec getBuildSpec() { return _buildSpec; }

  public Frame getTrainingFrame() { return _trainingFrame; }
  public Frame getValidationFrame() { return _validationFrame; }
  public Frame getBlendingFrame() { return _blendingFrame; }
  public Frame getLeaderboardFrame() { return _leaderboardFrame; }

  public Vec getResponseColumn() { return _responseColumn; }
  public Vec getFoldColumn() { return _foldColumn; }
  public Vec getWeightsColumn() { return _weightsColumn; }

  public StepDefinition[] getActualModelingSteps() { return _actualModelingSteps; }

  Frame _trainingFrame;    // required training frame: can add and remove Vecs, but not mutate Vec data in place.
  Frame _validationFrame;  // optional validation frame; the training_frame is split automatically if it's not specified.
  Frame _blendingFrame;    // optional blending frame for SE (usually if xval is disabled).
  Frame _leaderboardFrame; // optional test frame used for leaderboard scoring; if not specified, leaderboard will use xval metrics.

  Vec _responseColumn;
  Vec _foldColumn;
  Vec _weightsColumn;

  Date _startTime;
  Countdown _runCountdown;
  Job<AutoML> _job;                  // the Job object for the build of this AutoML.
  WorkAllocations _workAllocations;
  StepDefinition[] _modelingPlan;  // the input definition, that we plan to use/execute
  StepDefinition[] _actualModelingSteps; // the output definition, listing only the steps that were actually used

  private ModelingStepsRegistry _modelingStepsRegistry;
  private ModelingStepsExecutor _modelingStepsExecutor;
  private Leaderboard _leaderboard;
  private EventLog _eventLog;

  // check that we haven't messed up the original Frame
  private Vec[] _originalTrainingFrameVecs;
  private String[] _originalTrainingFrameNames;
  private long[] _originalTrainingFrameChecksums;
  private Key<Grid> _gridKeys[] = new Key[0];  // Grid key for the GridSearches
  private transient ModelingStep[] _executionPlan;

  public AutoML() {
    super(null);
  }

  public AutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {
    super(key == null ? Key.make(buildSpec.project()) : key);
    _startTime = startTime;
    _buildSpec = buildSpec;
    _runCountdown = Countdown.fromSeconds(buildSpec.build_control.stopping_criteria.max_runtime_secs());
    _modelingPlan = buildSpec.build_models.modeling_plan == null ? defaultModelingPlan : buildSpec.build_models.modeling_plan;
    _modelingStepsRegistry = new ModelingStepsRegistry();

    try {
      _eventLog = EventLog.getOrMake(_key);
      eventLog().info(Stage.Workflow, "Project: " + projectName());

      handleCVParameters(buildSpec);

      handleReproducibilityParameters(buildSpec);

      handleDatafileParameters(buildSpec);

      handleEarlyStoppingParameters(buildSpec);

      initLeaderboard(buildSpec);

      planWork();

      _modelingStepsExecutor = new ModelingStepsExecutor(_leaderboard, _eventLog, _runCountdown);
    } catch (Exception e) {
      delete(); //cleanup potentially leaked keys
      throw e;
    }
  }

  private void initLeaderboard(AutoMLBuildSpec buildSpec) {
    String sort_metric = buildSpec.input_spec.sort_metric == null ? null : buildSpec.input_spec.sort_metric.toLowerCase();
    _leaderboard = Leaderboard.getOrMake(_key.toString(), _eventLog, _leaderboardFrame, sort_metric);
  }

  private void handleReproducibilityParameters(AutoMLBuildSpec buildSpec) {
    eventLog().info(Stage.Workflow, "Build control seed: " + buildSpec.build_control.stopping_criteria.seed() +
            (buildSpec.build_control.stopping_criteria.seed() == -1 ? " (random)" : ""));
  }

  private void handleEarlyStoppingParameters(AutoMLBuildSpec buildSpec) {
    if (buildSpec.build_control.stopping_criteria.stopping_tolerance() == AUTO_STOPPING_TOLERANCE) {
      buildSpec.build_control.stopping_criteria.set_default_stopping_tolerance_for_frame(_trainingFrame);
      eventLog().info(Stage.Workflow, "Setting stopping tolerance adaptively based on the training frame: " +
              buildSpec.build_control.stopping_criteria.stopping_tolerance());
    } else {
      eventLog().info(Stage.Workflow, "Stopping tolerance set by the user: " + buildSpec.build_control.stopping_criteria.stopping_tolerance());
      double default_tolerance = AutoMLBuildSpec.AutoMLStoppingCriteria.default_stopping_tolerance_for_frame(_trainingFrame);
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

  ModelingStep[] getExecutionPlan() {
    return _executionPlan == null ? (_executionPlan = _modelingStepsRegistry.getOrderedSteps(_modelingPlan, this)) : _executionPlan;
  }

  void planWork() {
    if (_buildSpec.build_models.exclude_algos != null && _buildSpec.build_models.include_algos != null) {
      throw new  H2OIllegalArgumentException("Parameters `exclude_algos` and `include_algos` are mutually exclusive: please use only one of them if necessary.");
    }

    Set<Algo> skippedAlgos = new HashSet<>();
    if (_buildSpec.build_models.exclude_algos != null) {
      skippedAlgos.addAll(Arrays.asList(_buildSpec.build_models.exclude_algos));
    } else if (_buildSpec.build_models.include_algos != null) {
      skippedAlgos.addAll(Arrays.asList(Algo.values()));
      skippedAlgos.removeAll(Arrays.asList(_buildSpec.build_models.include_algos));
    }

    for (Algo algo : Algo.values()) {
      if (!skippedAlgos.contains(algo) && !algo.enabled()) {
        _eventLog.warn(Stage.ModelTraining, "AutoML: "+algo.name()+" is not available; skipping it.");
        skippedAlgos.add(algo);
      }
    }

    WorkAllocations workAllocations = new WorkAllocations();
    for (ModelingStep step: getExecutionPlan()) {
      workAllocations.allocate(step.makeWork());
    }
    for (Algo skippedAlgo : skippedAlgos) {
      eventLog().info(Stage.ModelTraining, "Disabling Algo: "+skippedAlgo+" as requested by the user.");
      workAllocations.remove(skippedAlgo);
    }
    workAllocations.freeze();
    _workAllocations = workAllocations;
  }

  @Override
  public void run() {
    _modelingStepsExecutor.start();
    eventLog().info(Stage.Workflow, "AutoML build started: " + EventLogEntry.dateTimeFormat.format(_runCountdown.start_time()))
            .setNamedValue("start_epoch", _runCountdown.start_time(), EventLogEntry.epochFormat);
    learn();
    stop();
  }

  @Override
  public void stop() {
    if (null == _modelingStepsExecutor) return; // already stopped
    _modelingStepsExecutor.stop();
    eventLog().info(Stage.Workflow, "AutoML build stopped: " + EventLogEntry.dateTimeFormat.format(_runCountdown.stop_time()))
            .setNamedValue("stop_epoch", _runCountdown.stop_time(), EventLogEntry.epochFormat);
    eventLog().info(Stage.Workflow, "AutoML build done: built " + _modelingStepsExecutor.modelCount() + " models");
    eventLog().info(Stage.Workflow, "AutoML duration: "+ PrettyPrint.msecs(_runCountdown.duration(), true))
            .setNamedValue("duration_secs", Math.round(_runCountdown.duration() / 1000.));

    Log.info(eventLog().toString("Event Log for AutoML Run " + _key + ":"));
    for (EventLogEntry event : eventLog()._events)
      Log.info(event);

    if (0 < leaderboard().getModelKeys().length) {
      Log.info(leaderboard().toTwoDimTable("Leaderboard for project " + projectName(), true).toString());
    }

    possiblyVerifyImmutability();
    if (!_buildSpec.build_control.keep_cross_validation_predictions) {
      cleanUpModelsCVPreds();
    }
  }

  /**
   * Holds until AutoML's job is completed, if a job exists.
   */
  public void get() {
    if (_job != null) _job.get();
  }

  public Job<AutoML> job() {
    if (null == _job) return null;
    return DKV.getGet(_job._key);
  }

  public Model leader() {
    return leaderboard() == null ? null : _leaderboard.getLeader();
  }

  public Leaderboard leaderboard() {
    return _leaderboard == null ? null : (_leaderboard = _leaderboard._key.get());
  }

  public EventLog eventLog() {
    return _eventLog == null ? null : (_eventLog = _eventLog._key.get());
  }

  public String projectName() {
    return _buildSpec == null ? null : _buildSpec.project();
  }

  public long timeRemainingMs() {
    return _runCountdown.remainingTime();
  }

  public int remainingModels() {
    if (_buildSpec.build_control.stopping_criteria.max_models() == 0)
      return Integer.MAX_VALUE;
    return _buildSpec.build_control.stopping_criteria.max_models() - _modelingStepsExecutor.modelCount();
  }

  @Override
  public boolean keepRunning() {
    return !_runCountdown.timedOut() && remainingModels() > 0;
  }

  boolean isCVEnabled() {
    return _buildSpec.build_control.nfolds != 0 || _buildSpec.input_spec.fold_column != null;
  }


  //*****************  Data Preparation Section  *****************//

  private void optionallySplitTrainingDataset() {
    // If no cross-validation and validation or leaderboard frame are missing,
    // then we need to create one out of the original training set.
    if (!isCVEnabled()) {
      double[] splitRatios = null;
      if (null == _validationFrame && null == _leaderboardFrame) {
        splitRatios = new double[]{ 0.8, 0.1, 0.1 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and none of validation frame and leaderboard frame were provided, " +
                "automatically split the training data into training, validation and leaderboard frames in the ratio 80/10/10");
      } else if (null == _validationFrame) {
        splitRatios = new double[]{ 0.9, 0.1, 0 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and no validation frame was provided, " +
                "automatically split the training data into training and validation frames in the ratio 90/10");
      } else if (null == _leaderboardFrame) {
        splitRatios = new double[]{ 0.9, 0, 0.1 };
        eventLog().info(Stage.DataImport,
            "Since cross-validation is disabled, and no leaderboard frame was provided, " +
                "automatically split the training data into training and leaderboard frames in the ratio 90/10");
      }
      if (splitRatios != null) {
        Key[] keys = new Key[] {
            Key.make("automl_training_"+ _origTrainingFrame._key),
            Key.make("automl_validation_"+ _origTrainingFrame._key),
            Key.make("automl_leaderboard_"+ _origTrainingFrame._key),
        };
        Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(
                _origTrainingFrame,
            keys,
            splitRatios,
            _buildSpec.build_control.stopping_criteria.seed()
        );
        _trainingFrame = splits[0];

        if (_validationFrame == null && splits[1].numRows() > 0) {
          _validationFrame = splits[1];
        } else {
          splits[1].delete();
        }

        if (_leaderboardFrame == null && splits[2].numRows() > 0) {
          _leaderboardFrame = splits[2];
        } else {
          splits[2].delete();
        }
      }
    }
  }
  private void handleDatafileParameters(AutoMLBuildSpec buildSpec) {
    _origTrainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
    _validationFrame = DKV.getGet(buildSpec.input_spec.validation_frame);
    _blendingFrame = DKV.getGet(buildSpec.input_spec.blending_frame);
    _leaderboardFrame = DKV.getGet(buildSpec.input_spec.leaderboard_frame);

    if (null == _origTrainingFrame)
      throw new H2OIllegalArgumentException("No training data has been specified, either as a path or a key.");

    Map<String, Frame> compatible_frames = new LinkedHashMap<String, Frame>(){{
      put("training", _origTrainingFrame);
      put("validation", _validationFrame);
      put("blending", _blendingFrame);
      put("leaderboard", _leaderboardFrame);
    }};
    for (Map.Entry<String, Frame> entry : compatible_frames.entrySet()) {
      Frame frame = entry.getValue();
      if (frame != null && frame.find(buildSpec.input_spec.response_column) == -1) {
        throw new H2OIllegalArgumentException("Response column '"+buildSpec.input_spec.response_column+"' is not in the "+entry.getKey()+" frame.");
      }
    }

    if (buildSpec.input_spec.fold_column != null && _origTrainingFrame.find(buildSpec.input_spec.fold_column) == -1) {
      throw new H2OIllegalArgumentException("Fold column '"+buildSpec.input_spec.fold_column+"' is not in the training frame.");
    }
    if (buildSpec.input_spec.weights_column != null && _origTrainingFrame.find(buildSpec.input_spec.weights_column) == -1) {
      throw new H2OIllegalArgumentException("Weights column '"+buildSpec.input_spec.weights_column+"' is not in the training frame.");
    }

    optionallySplitTrainingDataset();

    if (null == _trainingFrame) {
      // when nfolds>0, let trainingFrame be the original frame
      // but cloning to keep an internal ref just in case the original ref gets deleted from client side
      // (can occur in some corner cases with Python GC for example if frame get's out of scope during an AutoML rerun)
      _trainingFrame = new Frame(_origTrainingFrame);
      _trainingFrame._key = Key.make("automl_training_" + _origTrainingFrame._key);
      DKV.put(_trainingFrame);
    }

    _responseColumn = _trainingFrame.vec(buildSpec.input_spec.response_column);
    _foldColumn = _trainingFrame.vec(buildSpec.input_spec.fold_column);
    _weightsColumn = _trainingFrame.vec(buildSpec.input_spec.weights_column);

    eventLog().info(Stage.DataImport,
        "training frame: "+_trainingFrame.toString().replace("\n", " ")+" checksum: "+_trainingFrame.checksum());
    if (null != _validationFrame) {
      eventLog().info(Stage.DataImport,
          "validation frame: "+_validationFrame.toString().replace("\n", " ")+" checksum: "+_validationFrame.checksum());
    } else {
      eventLog().info(Stage.DataImport, "validation frame: NULL");
    }
    if (null != _leaderboardFrame) {
      eventLog().info(Stage.DataImport,
          "leaderboard frame: "+_leaderboardFrame.toString().replace("\n", " ")+" checksum: "+_leaderboardFrame.checksum());
    } else {
      eventLog().info(Stage.DataImport, "leaderboard frame: NULL");
    }

    eventLog().info(Stage.DataImport, "response column: "+buildSpec.input_spec.response_column);
    eventLog().info(Stage.DataImport, "fold column: "+_foldColumn);
    eventLog().info(Stage.DataImport, "weights column: "+_weightsColumn);

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      _originalTrainingFrameVecs = _origTrainingFrame.vecs().clone();
      _originalTrainingFrameNames = _origTrainingFrame.names().clone();
      _originalTrainingFrameChecksums = new long[_originalTrainingFrameVecs.length];

      for (int i = 0; i < _originalTrainingFrameVecs.length; i++)
        _originalTrainingFrameChecksums[i] = _originalTrainingFrameVecs[i].checksum();
    }
  }


  //*****************  Training Jobs  *****************//

  private void learn() {
    List<ModelingStep> executed = new ArrayList<>();
    for (ModelingStep step : getExecutionPlan()) {
        if (!exceededSearchLimits(step._description, step._ignoreConstraints)) {
          if (_modelingStepsExecutor.submit(step, job())) {
            executed.add(step);
          }
        }
    }
    _actualModelingSteps = _modelingStepsRegistry.createDefinitionPlanFromSteps(executed.toArray(new ModelingStep[0]));
    eventLog().info(Stage.Workflow, "Actual modeling steps: "+Arrays.toString(_actualModelingSteps));
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
    return Key.make(algoName + counterStr + "_AutoML_" + timestampFormatForKeys.format(_startTime));
  }

  Key<Grid> gridKey(String algoName, boolean with_counter) {
    String counterStr = with_counter ? "_" + nextInstanceCounter(algoName, gridInstanceCounters) : "";
    return Key.make(algoName + "_grid_" + counterStr + "_AutoML_" + timestampFormatForKeys.format(_startTime));
  }

  void addGridKey(Key<Grid> gridKey) {
    _gridKeys = Arrays.copyOf(_gridKeys, _gridKeys.length + 1);
    _gridKeys[_gridKeys.length - 1] = gridKey;
  }

  private boolean exceededSearchLimits(String modelDesc, boolean ignoreLimits) {
    if (_job.stop_requested()) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: job cancelled; skipping "+modelDesc);
      return true;
    }

    if (!ignoreLimits && _runCountdown.timedOut()) {
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
    Key<Job> jobKey = _job == null ? null : _job._key;
    Log.debug("Cleaning up AutoML "+jobKey);
    // If the Frame was made here (e.g. buildspec contained a path, then it will be deleted
    if (_buildSpec.input_spec.training_frame == null && _origTrainingFrame != null) {
      _origTrainingFrame.delete(jobKey, fs, true);
    }
    if (_buildSpec.input_spec.validation_frame == null && _validationFrame != null) {
      _validationFrame.delete(jobKey, fs, true);
    }
    if (_buildSpec.input_spec.leaderboard_frame == null && _leaderboardFrame != null) {
      _leaderboardFrame.delete(jobKey, fs, true);
    }

    if (_trainingFrame != null && _origTrainingFrame != null)
      Frame.deleteTempFrameAndItsNonSharedVecs(_trainingFrame, _origTrainingFrame);
    if (leaderboard() != null) leaderboard().remove(fs, cascade);
    if (eventLog() != null) eventLog().remove(fs, cascade);

    // grid should be removed after leaderboard cleanup
    if (_gridKeys != null)
      for (Key<Grid> gridKey : _gridKeys) Keyed.remove(gridKey, fs, true);

    return super.remove_impl(fs, cascade);
  }

  private boolean possiblyVerifyImmutability() {
    boolean warning = false;

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      eventLog().debug(Stage.Workflow, "Verifying training frame immutability. . .");

      Vec[] vecsRightNow = _origTrainingFrame.vecs();
      String[] namesRightNow = _origTrainingFrame.names();

      if (_originalTrainingFrameVecs.length != vecsRightNow.length) {
        Log.warn("Training frame vec count has changed from: " +
                _originalTrainingFrameVecs.length + " to: " + vecsRightNow.length);
        warning = true;
      }
      if (_originalTrainingFrameNames.length != namesRightNow.length) {
        Log.warn("Training frame vec count has changed from: " +
                _originalTrainingFrameNames.length + " to: " + namesRightNow.length);
        warning = true;
      }

      for (int i = 0; i < _originalTrainingFrameVecs.length; i++) {
        if (!_originalTrainingFrameVecs[i].equals(vecsRightNow[i])) {
          Log.warn("Training frame vec number " + i + " has changed keys.  Was: " +
                  _originalTrainingFrameVecs[i] + " , now: " + vecsRightNow[i]);
          warning = true;
        }
        if (!_originalTrainingFrameNames[i].equals(namesRightNow[i])) {
          Log.warn("Training frame vec number " + i + " has changed names.  Was: " +
                  _originalTrainingFrameNames[i] + " , now: " + namesRightNow[i]);
          warning = true;
        }
        if (_originalTrainingFrameChecksums[i] != vecsRightNow[i].checksum()) {
          Log.warn("Training frame vec number " + i + " has changed checksum.  Was: " +
                  _originalTrainingFrameChecksums[i] + " , now: " + vecsRightNow[i].checksum());
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
