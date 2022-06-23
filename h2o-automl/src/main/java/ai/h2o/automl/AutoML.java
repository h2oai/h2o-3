package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLBuildModels;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLInput;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria;
import ai.h2o.automl.StepResultState.ResultStatus;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.leaderboard.ModelGroup;
import ai.h2o.automl.leaderboard.ModelProvider;
import ai.h2o.automl.leaderboard.ModelStep;
import ai.h2o.automl.preprocessing.PreprocessingStep;
import hex.Model;
import hex.ScoreKeeper.StoppingMetric;
import hex.genmodel.utils.DistributionFamily;
import hex.leaderboard.*;
import hex.splitframe.ShuffleSplitFrame;
import water.*;
import water.automl.api.schemas3.AutoMLV99;
import water.exceptions.H2OAutoMLException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.nbhm.NonBlockingHashMap;
import water.util.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria.AUTO_STOPPING_TOLERANCE;


/**
 * H2O AutoML
 *
 * AutoML is used for automating the machine learning workflow, which includes automatic training and
 * tuning of many models within a user-specified time-limit. Stacked Ensembles will be automatically
 * trained on collections of individual models to produce highly predictive ensemble models which, in most cases,
 * will be the top performing models in the AutoML Leaderboard.
 */
public final class AutoML extends Lockable<AutoML> implements TimedH2ORunnable {

  public enum Constraint {
    MODEL_COUNT,
    TIMEOUT,
    FAILURE_COUNT,
  }

  public static final Comparator<AutoML> byStartTime = Comparator.comparing(a -> a._startTime);
  public static final String keySeparator = "@@";
  
  private static final int DEFAULT_MAX_CONSECUTIVE_MODEL_FAILURES = 10; 

  private static final boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with
  private static final ThreadLocal<SimpleDateFormat> timestampFormatForKeys = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd_HHmmss"));
  private static final Logger log = LoggerFactory.getLogger(AutoML.class);

  private static LeaderboardExtensionsProvider createLeaderboardExtensionProvider(AutoML automl) {
    final Key<AutoML> amlKey = automl._key;

    return new LeaderboardExtensionsProvider() {
      @Override
      public LeaderboardCell[] createExtensions(Model model) {
        final AutoML aml = amlKey.get();
        ModelingStep step = aml.session().getModelingStep(model.getKey());
        return new LeaderboardCell[] {
                new TrainingTime(model),
                new ScoringTimePerRow(model, aml.getLeaderboardFrame() == null ? aml.getTrainingFrame() : aml.getLeaderboardFrame()),
//                new ModelSize(model._key)
                new AlgoName(model),
                new ModelProvider(model, step),
                new ModelStep(model, step),
                new ModelGroup(model, step),
        };
      }
    };
  }

  /**
   * Instantiate an AutoML object and start it running.  Progress can be tracked via its job().
   *
   * @param buildSpec
   * @return a new running AutoML instance.
   */
  public static AutoML startAutoML(AutoMLBuildSpec buildSpec) {
    AutoML aml = new AutoML(buildSpec);
    aml.submit();
    return aml;
  }
  
  static AutoML startAutoML(AutoMLBuildSpec buildSpec, boolean testMode) {
    AutoML aml = new AutoML(buildSpec);
    aml._testMode = testMode;
    aml.submit();
    return aml;
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

  public DistributionFamily getDistributionFamily() {
    return _distributionFamily;
  }

  public double[] getClassDistribution() {
    if (_classDistribution == null)
      _classDistribution = (new MRUtils.ClassDist(_responseColumn)).doAll(_responseColumn).dist();
    return _classDistribution;
  }

  public StepDefinition[] getActualModelingSteps() { return _actualModelingSteps; }

  Frame _trainingFrame;    // required training frame: can add and remove Vecs, but not mutate Vec data in place.
  Frame _validationFrame;  // optional validation frame; the training_frame is split automatically if it's not specified.
  Frame _blendingFrame;    // optional blending frame for SE (usually if xval is disabled).
  Frame _leaderboardFrame; // optional test frame used for leaderboard scoring; if not specified, leaderboard will use xval metrics.

  Vec _responseColumn;
  Vec _foldColumn;
  Vec _weightsColumn;

  DistributionFamily _distributionFamily;
  private double[] _classDistribution;

  Date _startTime;
  Countdown _runCountdown;
  Job<AutoML> _job;                  // the Job object for the build of this AutoML.
  WorkAllocations _workAllocations;
  StepDefinition[] _actualModelingSteps; // the output definition, listing only the steps that were actually used

  int _maxConsecutiveModelFailures = DEFAULT_MAX_CONSECUTIVE_MODEL_FAILURES;
  AtomicInteger _consecutiveModelFailures = new AtomicInteger();
  AtomicLong _incrementalSeed = new AtomicLong();
  private String _runId;

  private ModelingStepsExecutor _modelingStepsExecutor;
  private AutoMLSession _session;
  private Leaderboard _leaderboard;
  private EventLog _eventLog;

  // check that we haven't messed up the original Frame
  private Vec[] _originalTrainingFrameVecs;
  private String[] _originalTrainingFrameNames;
  private long[] _originalTrainingFrameChecksums;
  private transient NonBlockingHashMap<Key, String> _trackedKeys = new NonBlockingHashMap<>();
  private transient ModelingStep[] _executionPlan;
  private transient PreprocessingStep[] _preprocessing;
  transient StepResultState[] _stepsResults;

  private boolean _useAutoBlending;
  private boolean _testMode;  // when on, internal states are kept for inspection
  /**
   * DO NOT USE explicitly: for schema/reflection only.
   */
  public AutoML() {
    super(null);
  }

  public AutoML(AutoMLBuildSpec buildSpec) {
    this(new Date(), buildSpec);
  }
  
  public AutoML(Key<AutoML> key, AutoMLBuildSpec buildSpec) {
    this(key, new Date(), buildSpec);
  }

  /**
   * @deprecated use {@link #AutoML(AutoMLBuildSpec) instead}
   */
  @Deprecated
  public AutoML(Date startTime, AutoMLBuildSpec buildSpec) {
    this(null, startTime, buildSpec);
  }

  /**
   * @deprecated use {@link #AutoML(Key, AutoMLBuildSpec) instead}
   */
  @Deprecated
  public AutoML(Key<AutoML> key, Date startTime, AutoMLBuildSpec buildSpec) {
    super(key == null ? buildSpec.makeKey() : key);
    try {
      _startTime = startTime;
      _session = AutoMLSession.getInstance(_key.toString());
      _eventLog = EventLog.getOrMake(_key);
      eventLog().info(Stage.Workflow, "Project: "+buildSpec.project());

      validateBuildSpec(buildSpec);
      _buildSpec = buildSpec;
      // now that buildSpec is validated, we can assign it: all future logic can now safely access parameters through _buildSpec.
      _runId = _buildSpec.instanceId();
      _runCountdown = Countdown.fromSeconds(_buildSpec.build_control.stopping_criteria.max_runtime_secs());
      _incrementalSeed.set(_buildSpec.build_control.stopping_criteria.seed());

      prepareData();
      initLeaderboard();
      initPreprocessing();
      _modelingStepsExecutor = new ModelingStepsExecutor(_leaderboard, _eventLog, _runCountdown);
    } catch (Exception e) {
      delete(); //cleanup potentially leaked keys
      throw e;
    }
  }

  /**
   * Validates all buildSpec parameters and provide reasonable defaults dynamically or parameter cleaning if necessary.
   *
   * Ideally, validation should be fast as we should be able to call it in the future
   * directly from client (e.g. Flow) to validate parameters before starting the AutoML run.
   * That's also the reason why validate methods should not modify data,
   * only possibly read them to validate parameters that may depend on data.
   *
   * In the future, we may also reuse ModelBuilder.ValidationMessage to return all validation results at once to the client (cf. ModelBuilder).
   *
   * @param buildSpec all the AutoML parameters to validate.
   */
  private void validateBuildSpec(AutoMLBuildSpec buildSpec) {
    validateInput(buildSpec.input_spec);
    validateModelValidation(buildSpec);
    validateModelBuilding(buildSpec.build_models);
    validateEarlyStopping(buildSpec.build_control.stopping_criteria, buildSpec.input_spec);
    validateReproducibility(buildSpec);
  }

  private void validateInput(AutoMLInput input) {
    if (DKV.getGet(input.training_frame) == null)
      throw new H2OIllegalArgumentException("No training data has been specified, either as a path or a key, or it is not available anymore.");

    final Frame trainingFrame = DKV.getGet(input.training_frame);
    final Frame validationFrame = DKV.getGet(input.validation_frame);
    final Frame blendingFrame = DKV.getGet(input.blending_frame);
    final Frame leaderboardFrame = DKV.getGet(input.leaderboard_frame);

    Map<String, Frame> compatibleFrames = new LinkedHashMap<String, Frame>(){{
      put("training", trainingFrame);
      put("validation", validationFrame);
      put("blending", blendingFrame);
      put("leaderboard", leaderboardFrame);
    }};
    for (Map.Entry<String, Frame> entry : compatibleFrames.entrySet()) {
      Frame frame = entry.getValue();
      if (frame != null && frame.find(input.response_column) < 0) {
        throw new H2OIllegalArgumentException("Response column '"+input.response_column+"' is not in the "+entry.getKey()+" frame.");
      }
    }

    if (input.fold_column != null && trainingFrame.find(input.fold_column) < 0) {
      throw new H2OIllegalArgumentException("Fold column '"+input.fold_column+"' is not in the training frame.");
    }
    if (input.weights_column != null && trainingFrame.find(input.weights_column) < 0) {
      throw new H2OIllegalArgumentException("Weights column '"+input.weights_column+"' is not in the training frame.");
    }

    if (input.ignored_columns != null) {
      List<String> ignoredColumns = new ArrayList<>(Arrays.asList(input.ignored_columns));
      Map<String, String> doNotIgnore = new LinkedHashMap<String, String>(){{
        put("response_column", input.response_column);
        put("fold_column", input.fold_column);
        put("weights_column", input.weights_column);
      }};
      for (Map.Entry<String, String> entry: doNotIgnore.entrySet()) {
        if (entry.getValue() != null && ignoredColumns.contains(entry.getValue())) {
          eventLog().info(Stage.Validation,
                  "Removing "+entry.getKey()+" '"+entry.getValue()+"' from list of ignored columns.");
          ignoredColumns.remove(entry.getValue());
        }
      }
      input.ignored_columns = ignoredColumns.toArray(new String[0]);
    }
  }


  private void validateModelValidation(AutoMLBuildSpec buildSpec) {
    if (buildSpec.input_spec.fold_column != null) {
      eventLog().warn(Stage.Validation, "Fold column " + buildSpec.input_spec.fold_column + " will be used for cross-validation. nfolds parameter will be ignored.");
      buildSpec.build_control.nfolds = 0;
    } else if (buildSpec.build_control.nfolds == -1) {
      Frame trainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
      long nrows = trainingFrame.numRows();
      long ncols = trainingFrame.numCols() - (buildSpec.getNonPredictors().length +
              (buildSpec.input_spec.ignored_columns != null ? buildSpec.input_spec.ignored_columns.length : 0));

      double max_runtime = buildSpec.build_control.stopping_criteria.max_runtime_secs();
      long nthreads = Stream.of(H2O.CLOUD.members())
              .mapToInt((h2o) -> h2o._heartbeat._nthreads)
              .sum();

      boolean use_blending = ((ncols * nrows) / (max_runtime * nthreads)) > 2064;
      if (max_runtime > 0 && use_blending &&
              !(buildSpec.build_control.keep_cross_validation_predictions ||
                buildSpec.build_control.keep_cross_validation_models ||
                buildSpec.build_control.keep_cross_validation_fold_assignment)) {
        _useAutoBlending = true;
        buildSpec.build_control.nfolds = 0;
        eventLog().info(Stage.Validation, "Blending will be used.");
      } else {
        buildSpec.build_control.nfolds = 5;
        eventLog().info(Stage.Validation, "5-fold cross-validation will be used.");
      }
    } else if (buildSpec.build_control.nfolds <= 1) {
      eventLog().info(Stage.Validation, "Cross-validation disabled by user: no fold column nor nfolds > 1.");
      buildSpec.build_control.nfolds = 0;
    }
    if ((buildSpec.build_control.nfolds > 0 || buildSpec.input_spec.fold_column != null)
            && DKV.getGet(buildSpec.input_spec.validation_frame) != null) {
      eventLog().warn(Stage.Validation, "User specified a validation frame with cross-validation still enabled."
              + " Please note that the models will still be validated using cross-validation only,"
              + " the validation frame will be used to provide purely informative validation metrics on the trained models.");
    }
    if (Arrays.asList(
            DistributionFamily.fractionalbinomial,
            DistributionFamily.quasibinomial,
            DistributionFamily.ordinal
            ).contains(buildSpec.build_control.distribution)) {
      throw new H2OIllegalArgumentException("Distribution \"" + buildSpec.build_control.distribution.name() + "\" is not supported in AutoML!");
    }
  }

  private void validateModelBuilding(AutoMLBuildModels modelBuilding) {
    if (modelBuilding.exclude_algos != null && modelBuilding.include_algos != null) {
      throw new  H2OIllegalArgumentException("Parameters `exclude_algos` and `include_algos` are mutually exclusive: please use only one of them if necessary.");
    }
    if (modelBuilding.exploitation_ratio > 1) {
      throw new H2OIllegalArgumentException("`exploitation_ratio` must be between 0 and 1.");
    }
  }
  
  private void validateEarlyStopping(AutoMLStoppingCriteria stoppingCriteria, AutoMLInput input) {
    if (stoppingCriteria.max_models() <= 0 && stoppingCriteria.max_runtime_secs() <= 0) {
      stoppingCriteria.set_max_runtime_secs(3600);
      eventLog().info(Stage.Validation, "User didn't set any runtime constraints (max runtime or max models), using default 1h time limit");
    }
    Frame refFrame = DKV.getGet(input.training_frame);
    if (stoppingCriteria.stopping_tolerance() == AUTO_STOPPING_TOLERANCE) {
      stoppingCriteria.set_default_stopping_tolerance_for_frame(refFrame);
      eventLog().info(Stage.Validation, "Setting stopping tolerance adaptively based on the training frame: "+stoppingCriteria.stopping_tolerance());
    } else {
      eventLog().info(Stage.Validation, "Stopping tolerance set by the user: "+stoppingCriteria.stopping_tolerance());
      double defaultTolerance = AutoMLStoppingCriteria.default_stopping_tolerance_for_frame(refFrame);
      if (stoppingCriteria.stopping_tolerance() < 0.7 * defaultTolerance){
        eventLog().warn(Stage.Validation, "Stopping tolerance set by the user is < 70% of the recommended default of "+defaultTolerance+", so models may take a long time to converge or may not converge at all.");
      }
    }
  }


  private void validateReproducibility(AutoMLBuildSpec buildSpec) {
    eventLog().info(Stage.Validation, "Build control seed: " + buildSpec.build_control.stopping_criteria.seed() +
            (buildSpec.build_control.stopping_criteria.seed() == -1 ? " (random)" : ""));
  }

  private void initLeaderboard() {
    String sortMetric = _buildSpec.input_spec.sort_metric;
    sortMetric = sortMetric == null || StoppingMetric.AUTO.name().equalsIgnoreCase(sortMetric) ? null : sortMetric.toLowerCase();
    if ("deviance".equalsIgnoreCase(sortMetric)) {
        sortMetric = "mean_residual_deviance"; //compatibility with names used in leaderboard
    }
    _leaderboard = Leaderboard.getInstance(_key.toString(), eventLog().asLogger(Stage.ModelTraining), _leaderboardFrame, sortMetric, Leaderboard.ScoreData.auto);
    if (null != _leaderboard) {
      eventLog().warn(Stage.Workflow, "New models will be added to existing leaderboard "+_key.toString()
              +" (leaderboard frame="+(_leaderboardFrame == null ? null : _leaderboardFrame._key)+") with already "+_leaderboard.getModelKeys().length+" models.");
    } else {
      _leaderboard = Leaderboard.getOrMake(_key.toString(), eventLog().asLogger(Stage.ModelTraining), _leaderboardFrame, sortMetric, Leaderboard.ScoreData.auto);
    }
    _leaderboard.setExtensionsProvider(createLeaderboardExtensionProvider(this));
  }

  private void initPreprocessing() {
    _preprocessing = _buildSpec.build_models.preprocessing == null 
            ? null 
            : Arrays.stream(_buildSpec.build_models.preprocessing)
                .map(def -> def.newPreprocessingStep(this))
                .toArray(PreprocessingStep[]::new);
  }
  
  PreprocessingStep[] getPreprocessing() {
    return _preprocessing;
  }

  ModelingStep[] getExecutionPlan() {
    if (_executionPlan == null) {
      _executionPlan = session().getModelingStepsRegistry().getOrderedSteps(selectModelingPlan(null), this);
    }
    return _executionPlan;
  }

  StepDefinition[] selectModelingPlan(StepDefinition[] plan) {
    if (_buildSpec.build_models.modeling_plan == null) {
      // as soon as user specifies max_models, consider that user expects reproducibility.
      _buildSpec.build_models.modeling_plan = plan != null ? plan
              : _buildSpec.build_control.stopping_criteria.max_models() > 0 ? ModelingPlans.REPRODUCIBLE
              : ModelingPlans.defaultPlan();
    }
    return _buildSpec.build_models.modeling_plan;
  }

  void planWork() {
    Set<IAlgo> skippedAlgos = new HashSet<>();
    if (_buildSpec.build_models.exclude_algos != null) {
      skippedAlgos.addAll(Arrays.asList(_buildSpec.build_models.exclude_algos));
    } else if (_buildSpec.build_models.include_algos != null) {
      skippedAlgos.addAll(Arrays.asList(Algo.values()));
      skippedAlgos.removeAll(Arrays.asList(_buildSpec.build_models.include_algos));
    }

    for (Algo algo : Algo.values()) {
      if (!skippedAlgos.contains(algo) && !algo.enabled()) {
        boolean isMultinode = H2O.CLOUD.size() > 1;
        _eventLog.warn(Stage.Workflow,
                isMultinode ? "AutoML: "+algo.name()+" is not available in multi-node cluster; skipping it."
                        + " See http://docs.h2o.ai/h2o/latest-stable/h2o-docs/automl.html#experimental-features for details."
                        : "AutoML: "+algo.name()+" is not available; skipping it."
        );
        skippedAlgos.add(algo);
      }
    }

    WorkAllocations workAllocations = new WorkAllocations();
    for (ModelingStep step: getExecutionPlan()) {
      workAllocations.allocate(step.makeWork());
    }
    for (IAlgo skippedAlgo : skippedAlgos) {
      eventLog().info(Stage.Workflow, "Disabling Algo: "+skippedAlgo+" as requested by the user.");
      workAllocations.remove(skippedAlgo);
    }
    eventLog().debug(Stage.Workflow, "Defined work allocations: "+workAllocations);
    distributeExplorationVsExploitationWork(workAllocations);
    eventLog().debug(Stage.Workflow, "Actual work allocations: "+workAllocations);
    workAllocations.freeze();
    _workAllocations = workAllocations;
  }

  private void distributeExplorationVsExploitationWork(WorkAllocations allocations) {
    if (_buildSpec.build_models.exploitation_ratio < 0) return;
    int sumExploration = allocations.remainingWork(ModelingStep.isExplorationWork);
    int sumExploitation = allocations.remainingWork(ModelingStep.isExploitationWork);
    double explorationRatio = 1 - _buildSpec.build_models.exploitation_ratio;
    int newTotal = (int)Math.round(sumExploration / explorationRatio);
    int newSumExploration = sumExploration; // keeping the same weight for exploration steps (principle of less surprise).
    int newSumExploitation = newTotal - newSumExploration;
    for (Work work : allocations.getAllocations(ModelingStep.isExplorationWork)) {
      work._weight = (int)Math.round((double)work._weight * newSumExploration/sumExploration);
    }
    for (Work work : allocations.getAllocations(ModelingStep.isExploitationWork)) {
      work._weight = (int)Math.round((double)work._weight * newSumExploitation/sumExploitation);
    }
  }

  /**
   * Creates a job for the current AutoML instance and submits it to the task runner.
   * Calling this on an already running AutoML instance has no effect.
   */
  public void submit() {
    if (_job == null || !_job.isRunning()) {
      planWork();
      H2OJob<AutoML> j = new H2OJob<>(this, _key, _runCountdown.remainingTime());
      _job = j._job;
      eventLog().info(Stage.Workflow, "AutoML job created: " + EventLogEntry.dateTimeFormat.get().format(_startTime))
              .setNamedValue("creation_epoch", _startTime, EventLogEntry.epochFormat.get());
      j.start(_workAllocations.remainingWork());
      DKV.put(this);
    }
  }

  @Override
  public void run() {
    _modelingStepsExecutor.start();
    eventLog().info(Stage.Workflow, "AutoML build started: " + EventLogEntry.dateTimeFormat.get().format(_runCountdown.start_time()))
            .setNamedValue("start_epoch", _runCountdown.start_time(), EventLogEntry.epochFormat.get());
    try {
      learn();
    } finally {
      stop();
    }
  }
  
  @Override
  public void stop() {
    if (null == _modelingStepsExecutor) return; // already stopped
    _modelingStepsExecutor.stop();
    eventLog().info(Stage.Workflow, "AutoML build stopped: " + EventLogEntry.dateTimeFormat.get().format(_runCountdown.stop_time()))
            .setNamedValue("stop_epoch", _runCountdown.stop_time(), EventLogEntry.epochFormat.get());
    eventLog().info(Stage.Workflow, "AutoML build done: built " + _modelingStepsExecutor.modelCount() + " models");
    eventLog().info(Stage.Workflow, "AutoML duration: "+ PrettyPrint.msecs(_runCountdown.duration(), true))
            .setNamedValue("duration_secs", Math.round(_runCountdown.duration() / 1000.));

    log.info("AutoML run summary:");
    for (EventLogEntry event : eventLog()._events)
      log.info(event.toString());
    if (0 < leaderboard().getModelKeys().length) {
      log.info(leaderboard().toLogString());
    } else {
      long max_runtime_secs = (long)_buildSpec.build_control.stopping_criteria.max_runtime_secs();
      eventLog().warn(Stage.Workflow, "Empty leaderboard.\n"
              +"AutoML was not able to build any model within a max runtime constraint of "+max_runtime_secs+" seconds, "
              +"you may want to increase this value before retrying.");
    }

    session().detach();
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
  
  public AutoMLSession session() {
    _session = _session == null ? null : _session._key.get();
    if (_session != null) _session.attach(this, false);
    return _session;
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

  public boolean isCVEnabled() {
    return _buildSpec.build_control.nfolds > 0 || _buildSpec.input_spec.fold_column != null;
  }
  

  //*****************  Data Preparation Section  *****************//

  private void optionallySplitTrainingDataset() {
    // If no cross-validation and validation or leaderboard frame are missing,
    // then we need to create one out of the original training set.
    if (!isCVEnabled()) {
      double[] splitRatios = null;
      double validationRatio = null == _validationFrame ? 0.1 : 0;
      double blendingRatio = (_useAutoBlending && null == _blendingFrame) ? 0.2 : 0;
      if (validationRatio + blendingRatio > 0) {
        splitRatios = new double[]{
                1 - (validationRatio + blendingRatio),
                validationRatio,
                blendingRatio
        };
        ArrayList<String> frames = new ArrayList();
        if (null == _validationFrame) frames.add("validation");
        if (null == _blendingFrame && _useAutoBlending) frames.add("blending");

        String framesStr = String.join(", ", frames);
        String ratioStr = Arrays.stream(splitRatios)
                .mapToObj(d -> Integer.toString((int) (d * 100)))
                .collect(Collectors.joining("/"));
        eventLog().info(Stage.DataImport, "Since cross-validation is disabled, and " + framesStr + " frame(s) were not provided, " +
                "automatically split the training data into training, " + framesStr + " frame(s) in the ratio " + ratioStr + ".");
      }
      if (splitRatios != null) {
        Key[] keys = new Key[] {
            Key.make(_runId+"_training_"+ _origTrainingFrame._key),
            Key.make(_runId+"_validation_"+ _origTrainingFrame._key),
            Key.make(_runId+"_blending_"+ _origTrainingFrame._key),
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

        if (_blendingFrame == null && splits[2].numRows() > 0) {
          _blendingFrame = splits[2];
        } else {
          splits[2].delete();
        }
      }
      if (_leaderboardFrame == null)
        _leaderboardFrame = _validationFrame;
    }
  }

  private DistributionFamily inferDistribution(Vec response) {
    int numOfDomains = response.domain() == null ? 0 : response.domain().length;
    if (_buildSpec.build_control.distribution == DistributionFamily.AUTO) {
      if (numOfDomains == 0)
        return DistributionFamily.gaussian;
      if (numOfDomains == 2)
        return DistributionFamily.bernoulli;
      if (numOfDomains > 2)
        return DistributionFamily.multinomial;

      throw new RuntimeException("Number of classes is equal to 1.");
    } else {
      DistributionFamily distribution = _buildSpec.build_control.distribution;
      if (numOfDomains > 2) {
        if (!Arrays.asList(
                DistributionFamily.multinomial,
                DistributionFamily.ordinal,
                DistributionFamily.custom
        ).contains(distribution)) {
          throw new H2OAutoMLException("Wrong distribution specified! Number of classes of response is greater than 2." +
                  " Possible distribution values: \"multinomial\"," +
                  /*" \"ordinal\"," + */ // Currently unsupported in AutoML
                  " \"custom\".");
        }
      } else if (numOfDomains == 2) {
        if (!Arrays.asList(
                DistributionFamily.bernoulli,
                DistributionFamily.quasibinomial,
                DistributionFamily.fractionalbinomial,
                DistributionFamily.custom
        ).contains(distribution)) {
          throw new H2OAutoMLException("Wrong distribution specified! Number of classes of response is 2." +
                  " Possible distribution values: \"bernoulli\"," +
                  /*" \"quasibinomial\", \"fractionalbinomial\"," + */ // Currently unsupported in AutoML
                  " \"custom\".");
        }
      } else {
        if (!Arrays.asList(
                DistributionFamily.gaussian,
                DistributionFamily.poisson,
                DistributionFamily.negativebinomial,
                DistributionFamily.gamma,
                DistributionFamily.laplace,
                DistributionFamily.quantile,
                DistributionFamily.huber,
                DistributionFamily.tweedie,
                DistributionFamily.custom
        ).contains(distribution)) {
          throw new H2OAutoMLException("Wrong distribution specified! Response type suggests a regression task." +
                  " Possible distribution values: \"gaussian\", \"poisson\", \"negativebinomial\", \"gamma\", " +
                  "\"laplace\", \"quantile\", \"huber\", \"tweedie\", \"custom\".");
        }
      }
    return distribution;
    }
  }

  private void prepareData() {
    final AutoMLInput input = _buildSpec.input_spec;
    _origTrainingFrame = DKV.getGet(input.training_frame);
    _validationFrame = DKV.getGet(input.validation_frame);
    _blendingFrame = DKV.getGet(input.blending_frame);
    _leaderboardFrame = DKV.getGet(input.leaderboard_frame);

    optionallySplitTrainingDataset();

    if (null == _trainingFrame) {
      // when nfolds>0, let trainingFrame be the original frame
      // but cloning to keep an internal ref just in case the original ref gets deleted from client side
      // (can occur in some corner cases with Python GC for example if frame get's out of scope during an AutoML rerun)
      _trainingFrame = new Frame(_origTrainingFrame);
      _trainingFrame._key = Key.make(_runId+"_training_" + _origTrainingFrame._key);
      DKV.put(_trainingFrame);
    }

    _responseColumn = _trainingFrame.vec(input.response_column);
    _foldColumn = _trainingFrame.vec(input.fold_column);
    _weightsColumn = _trainingFrame.vec(input.weights_column);

    _distributionFamily = inferDistribution(_responseColumn);

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
    if (null != _blendingFrame) {
      this.eventLog().info(Stage.DataImport,
          "blending frame: "+_blendingFrame.toString().replace("\n", " ")+" checksum: "+_blendingFrame.checksum());
    } else {
      this.eventLog().info(Stage.DataImport, "blending frame: NULL");
    }

    eventLog().info(Stage.DataImport, "response column: "+input.response_column);
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
    List<ModelingStep> completed = new ArrayList<>();
    if (_preprocessing != null) {
      for (PreprocessingStep preprocessingStep : _preprocessing) preprocessingStep.prepare();
    }
    for (ModelingStep step : getExecutionPlan()) {
      if (!exceededSearchLimits(step)) {
        StepResultState state = _modelingStepsExecutor.submit(step, job());
        log.info("AutoML step returned with state: "+state.toString());
        if (_testMode) _stepsResults = ArrayUtils.append(_stepsResults, state);
        if (state.is(ResultStatus.success)) {
          _consecutiveModelFailures.set(0);
          completed.add(step);
        } else if (state.is(ResultStatus.failed)) {
          if (!step.ignores(Constraint.FAILURE_COUNT) 
                  && _consecutiveModelFailures.incrementAndGet() >= _maxConsecutiveModelFailures) {
            throw new H2OAutoMLException("Aborting AutoML after too many consecutive model failures", state.error());
          }
          if (state.error() instanceof H2OAutoMLException) { // if a step throws this exception, this will immediately abort the entire AutoML run.
            throw (H2OAutoMLException) state.error();
          }
        }
      }
    }
    if (_preprocessing != null) {
      for (PreprocessingStep preprocessingStep : _preprocessing) preprocessingStep.dispose();
    }
    _actualModelingSteps = session().getModelingStepsRegistry().createDefinitionPlanFromSteps(completed.toArray(new ModelingStep[0]));
    eventLog().info(Stage.Workflow, "Actual modeling steps: "+Arrays.toString(_actualModelingSteps));
  }

  public Key makeKey(String algoName, String type, boolean with_counter) {
    List<String> tokens = new ArrayList<>();
    tokens.add(algoName);
    if (!StringUtils.isNullOrEmpty(type)) tokens.add(type);
    if (with_counter) tokens.add(Integer.toString(session().nextModelCounter(algoName, type)));
    tokens.add(_runId);
    return Key.make(String.join("_", tokens));
  }

  public void trackKeys(Key... keys) {
    String whereFrom = Arrays.toString(Thread.currentThread().getStackTrace());
    for (Key key : keys) _trackedKeys.put(key, whereFrom);
  }

  private boolean exceededSearchLimits(ModelingStep step) {
    if (_job.stop_requested()) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: job cancelled; skipping "+step._description);
      return true;
    }

    if (!step.ignores(Constraint.TIMEOUT) && _runCountdown.timedOut()) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: out of time; skipping "+step._description);
      return true;
    }

    if (!step.ignores(Constraint.MODEL_COUNT) && remainingModels() <= 0) {
      eventLog().debug(EventLogEntry.Stage.ModelTraining, "AutoML: hit the max_models limit; skipping "+step._description);
      return true;
    }
    return false;
  }

  //*****************  Clean Up + other utility functions *****************//

  /**
   * Delete the AutoML-related objects, including the grids and models that it built if cascade=true
   */
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    Key<Job> jobKey = _job == null ? null : _job._key;
    log.debug("Cleaning up AutoML "+jobKey);
    if (_buildSpec != null) {
      // If the Frame was made here (e.g. buildspec contained a path, then it will be deleted
      if (_buildSpec.input_spec.training_frame == null && _origTrainingFrame != null) {
        _origTrainingFrame.delete(jobKey, fs, true);
      }
      if (_buildSpec.input_spec.validation_frame == null && _validationFrame != null) {
        _validationFrame.delete(jobKey, fs, true);
      }
    }
    if (_trainingFrame != null && _origTrainingFrame != null)
      Frame.deleteTempFrameAndItsNonSharedVecs(_trainingFrame, _origTrainingFrame);
    if (leaderboard() != null) leaderboard().remove(fs, cascade);
    if (eventLog() != null) eventLog().remove(fs, cascade);
    if (session() != null) session().remove(fs, cascade);
    if (cascade && _preprocessing != null) {
      for (PreprocessingStep preprocessingStep : _preprocessing) {
        preprocessingStep.remove();
      }
    }
    for (Key key : _trackedKeys.keySet()) Keyed.remove(key, fs, true);

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
        log.warn("Training frame vec count has changed from: " +
                _originalTrainingFrameVecs.length + " to: " + vecsRightNow.length);
        warning = true;
      }
      if (_originalTrainingFrameNames.length != namesRightNow.length) {
        log.warn("Training frame vec count has changed from: " +
                _originalTrainingFrameNames.length + " to: " + namesRightNow.length);
        warning = true;
      }

      for (int i = 0; i < _originalTrainingFrameVecs.length; i++) {
        if (!_originalTrainingFrameVecs[i].equals(vecsRightNow[i])) {
          log.warn("Training frame vec number " + i + " has changed keys.  Was: " +
                  _originalTrainingFrameVecs[i] + " , now: " + vecsRightNow[i]);
          warning = true;
        }
        if (!_originalTrainingFrameNames[i].equals(namesRightNow[i])) {
          log.warn("Training frame vec number " + i + " has changed names.  Was: " +
                  _originalTrainingFrameNames[i] + " , now: " + namesRightNow[i]);
          warning = true;
        }
        if (_originalTrainingFrameChecksums[i] != vecsRightNow[i].checksum()) {
          log.warn("Training frame vec number " + i + " has changed checksum.  Was: " +
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
    log.info("Cleaning up all CV Predictions for AutoML");
    for (Model model : leaderboard().getModels()) {
        model.deleteCrossValidationPreds();
    }
  }
}
