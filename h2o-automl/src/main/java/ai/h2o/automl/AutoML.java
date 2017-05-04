package ai.h2o.automl;

import ai.h2o.automl.UserFeedbackEvent.Stage;
import ai.h2o.automl.utils.AutoMLUtils;
import hex.Model;
import hex.ModelBuilder;
import hex.StackedEnsembleModel;
import hex.deeplearning.DeepLearningModel;
import hex.deepwater.DeepWater;
import hex.deepwater.DeepWaterParameters;
import hex.glm.GLMModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.SharedTreeModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.deepwater.DeepWaterModel;
import water.*;
import water.api.schemas3.ImportFilesV3;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static hex.deeplearning.DeepLearningModel.DeepLearningParameters.Activation.RectifierWithDropout;
import static water.Key.make;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing concurrent
 * strategies of execution in an effort to discover an optimal supervised model for some
 * given (dataset, response, loss) combo.
 */
public final class AutoML extends Lockable<AutoML> implements TimedH2ORunnable {

  private final static boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with
  private final static SimpleDateFormat fullTimestampFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.S");

  private AutoMLBuildSpec buildSpec;     // all parameters for doing this AutoML build
  private Frame origTrainingFrame;       // untouched original training frame
  private boolean didValidationSplit = false;
  private boolean didTestSplit = false;

  public AutoMLBuildSpec getBuildSpec() {
    return buildSpec;
  }

  public Frame getTrainingFrame() {
    return trainingFrame;
  }

  public Frame getValidationFrame() {
    return validationFrame;
  }

  public Vec getResponseColumn() {
    return responseColumn;
  }

  public FrameMetadata getFrameMetadata() {
    return frameMetadata;
  }

  private Frame trainingFrame;   // munged training frame: can add and remove Vecs, but not mutate Vec data in place
  private Frame validationFrame; // optional validation frame; the training_frame is split automagically if it's not specified
  private Frame testFrame;       // optional test frame used for leaderboard scoring; the validation_frame is split automagically if it's not specified

  private Vec responseColumn;
  FrameMetadata frameMetadata;           // metadata for trainingFrame

  // TODO: remove dead code
  // TODO: more than one grid key!
  private Key<Grid> gridKey;             // Grid key from GridSearch
  private boolean isClassification;

  private long stopTimeMs;
  private Job job;                  // the Job object for the build of this AutoML.  TODO: can we have > 1?

  private transient ArrayList<Job> jobs;
  private transient ArrayList<Frame> tempFrames;

  private AtomicInteger modelCount = new AtomicInteger();  // prepare for concurrency
  private Leaderboard leaderboard;
  private UserFeedback userFeedback;

  // check that we haven't messed up the original Frame
  private Vec[] originalTrainingFrameVecs;
  private String[] originalTrainingFrameNames;
  private long[] originalTrainingFrameChecksums;


  // TODO: UGH: this should be dynamic, and it's easy to make it so
  public enum algo {
    RF, GBM, GLM, GLRM, DL, KMEANS
  }  // consider EnumSet

  public AutoML() {
    super(null);
  }
  // https://0xdata.atlassian.net/browse/STEAM-52  --more interesting user options
  public AutoML(Key<AutoML> key, AutoMLBuildSpec buildSpec) {
    super(key);

    Date startTime = new Date();

    userFeedback = new UserFeedback(this); // Don't use until we set this.project

    this.buildSpec = buildSpec;

    userFeedback.info(Stage.Workflow, "AutoML job created: " + fullTimestampFormat.format(startTime));

    handleDatafileParameters(buildSpec);

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

      double default_tolerance = HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(this.trainingFrame);
      if (this.buildSpec.build_control.stopping_criteria._stopping_tolerance < 0.7 * default_tolerance){
        userFeedback.warn(Stage.Workflow, "Stopping tolerance set by the user is < 70% of the recommended default of " + default_tolerance + ", so models may take a long time to converge or may not converge at all.");
      }
    }

    userFeedback.info(Stage.Workflow, "Project: " + project());
    leaderboard = new Leaderboard(project(), userFeedback, this.testFrame);

    /*
    TODO
    if( excludeAlgos!=null ) {
      HashSet<algo> m = new HashSet<>();
      Collections.addAll(m,excludeAlgos);
      _excludeAlgos = m.toArray(new algo[m.size()]);
    } else _excludeAlgos =null;
    _allowMutations=tryMutations;
    */

    this.jobs = new ArrayList<>();
    this.tempFrames = new ArrayList<>();
  }

  /**
   * If the user hasn't specified validation or test data split it off for them.
   *                                                             <p>
   * The user can specify:                                       <p>
   * 1. training only                                            <p>
   * 2. training + validation                                    <p>
   * 3. training + test                                          <p>
   * 4. training + validation + test                             <p>
   *                                                             <p>
   * In the top three cases we auto-split:                       <p>
   * 1. training -> training:validation:test 70:15:15            <p>
   * 2. validation -> validation:test 50:50                      <p>
   * 3. training -> training:validation 70:30, test used as-is   <p>
   *                                                             <p>
   * TODO: should the size of the splits adapt to origTrainingFrame.numRows()?
   */
  private void optionallySplitDatasets() {
    if (null == this.validationFrame && null == this.testFrame) {
      // case 1:
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(origTrainingFrame,
              new Key[] { Key.make("training_" + origTrainingFrame._key),
                      Key.make("validation_" + origTrainingFrame._key),
                      Key.make("test_" + origTrainingFrame._key)},
              new double[] { 0.7, 0.15, 0.15 },
              buildSpec.build_control.stopping_criteria.seed());
      this.trainingFrame = splits[0];
      this.validationFrame = splits[1];
      this.testFrame = splits[2];
      this.didValidationSplit = true;
      this.didTestSplit = true;
      userFeedback.info(Stage.DataImport, "Automatically split the training data into training, validation and test datasets in the ratio 0.70:0.15:0.15");

    } else if (null != this.validationFrame && null == this.testFrame) {
      // case 2:
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(validationFrame,
              new Key[] { Key.make("validation_" + origTrainingFrame._key),
                      Key.make("test_" + origTrainingFrame._key)},
              new double[] { 0.5, 0.5 },
              buildSpec.build_control.stopping_criteria.seed());
      this.validationFrame = splits[0];
      this.testFrame = splits[1];
      this.didValidationSplit = true;
      this.didTestSplit = true;
      userFeedback.info(Stage.DataImport, "Automatically split the validation data into validation and test datasets in the ratio 0.5:0.5");

    } else if (null == this.validationFrame && null != this.testFrame) {
      // case 3:
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(origTrainingFrame,
              new Key[] { Key.make("training_" + origTrainingFrame._key),
                      Key.make("validation_" + origTrainingFrame._key)},
              new double[] { 0.7, 0.3 },
              buildSpec.build_control.stopping_criteria.seed());

      this.trainingFrame = splits[0];
      this.validationFrame = splits[1];
      this.didValidationSplit = true;
      this.didTestSplit = false;
      userFeedback.info(Stage.DataImport, "Automatically split the training data into training and validation datasets in the ratio 0.5:0.5");
    } else if (null != this.validationFrame && null != this.testFrame) {
      // case 4: leave things as-is
      userFeedback.info(Stage.DataImport, "Training, validation and test datasets were all specified; not auto-splitting.");
    } else {
      // can't happen
      throw new UnsupportedOperationException("Bad code in handleDatafileParameters");
    }
  }

  private void handleDatafileParameters(AutoMLBuildSpec buildSpec) {
    this.origTrainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
    this.validationFrame = DKV.getGet(buildSpec.input_spec.validation_frame);
    this.testFrame = DKV.getGet(buildSpec.input_spec.test_frame);

    if (null == buildSpec.input_spec.training_frame && null != buildSpec.input_spec.training_path)
      this.origTrainingFrame = importParseFrame(buildSpec.input_spec.training_path, buildSpec.input_spec.parse_setup);
    if (null == buildSpec.input_spec.validation_frame && null != buildSpec.input_spec.validation_path)
      this.validationFrame = importParseFrame(buildSpec.input_spec.validation_path, buildSpec.input_spec.parse_setup);
    if (null == buildSpec.input_spec.test_frame && null != buildSpec.input_spec.test_path)
      this.testFrame = importParseFrame(buildSpec.input_spec.test_path, buildSpec.input_spec.parse_setup);

    if (null == this.origTrainingFrame)
      throw new H2OIllegalArgumentException("No training frame; user specified training_path: " +
              buildSpec.input_spec.training_path +
              " and training_frame: " + buildSpec.input_spec.training_frame);

    if (this.origTrainingFrame.find(buildSpec.input_spec.response_column) == -1) {
      throw new H2OIllegalArgumentException("Response column " + buildSpec.input_spec.response_column + "is not in " +
              "the training frame.");
    }

    optionallySplitDatasets();

    if (null == this.trainingFrame) {
      // we didn't need to split off the validation_frame or test_frame ourselves
      this.trainingFrame = new Frame(origTrainingFrame);
      DKV.put(this.trainingFrame);
    }

    this.responseColumn = trainingFrame.vec(buildSpec.input_spec.response_column);

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      originalTrainingFrameVecs = origTrainingFrame.vecs().clone();
      originalTrainingFrameNames = origTrainingFrame.names().clone();
      originalTrainingFrameChecksums = new long[originalTrainingFrameVecs.length];

      for (int i = 0; i < originalTrainingFrameVecs.length; i++)
        originalTrainingFrameChecksums[i] = originalTrainingFrameVecs[i].checksum();
    }
  }


  public static AutoML makeAutoML(Key<AutoML> key, AutoMLBuildSpec buildSpec) {
    // if (buildSpec.input_spec.parse_setup == null)
    //   buildSpec.input_spec.parse_setup = ParseSetup.guessSetup(); // use defaults!

    AutoML autoML = new AutoML(key, buildSpec);

    if (null == autoML.trainingFrame)
      throw new H2OIllegalArgumentException("No training data has been specified, either as a path or a key.");

    /*
      TODO: joins
    Frame[] relations = null==relationPaths?null:new Frame[relationPaths.length];
    if( null!=relationPaths )
      for(int i=0;i<relationPaths.length;++i)
        relations[i] = importParseFrame(relationPaths[i]);
        */

    return autoML;
  }

  private static Frame importParseFrame(ImportFilesV3.ImportFiles importFiles, ParseSetup userSetup) {
    ArrayList<String> files = new ArrayList();
    ArrayList<String> keys = new ArrayList();
    ArrayList<String> fails = new ArrayList();
    ArrayList<String> dels = new ArrayList();

    H2O.getPM().importFiles(importFiles.path, null, files, keys, fails, dels);

    importFiles.files = files.toArray(new String[0]);
    importFiles.destination_frames = keys.toArray(new String[0]);
    importFiles.fails = fails.toArray(new String[0]);
    importFiles.dels = dels.toArray(new String[0]);

    String datasetName = importFiles.path.split("\\.(?=[^\\.]+$)")[0];
    String separatorRegex = (File.separator.equals("/") ? "/" : "\\");
    String[] pathPieces = datasetName.split(separatorRegex);
    datasetName = pathPieces[pathPieces.length - 1];

    Key[] realKeys = new Key[keys.size()];
    for (int i = 0; i < keys.size(); i++)
      realKeys[i] = make(keys.get(i));

    // TODO: we always have to tell guessSetup about single quotes?!
    ParseSetup guessedParseSetup = ParseSetup.guessSetup(realKeys, false, ParseSetup.GUESS_HEADER);

    return ParseDataset.parse(make(datasetName), realKeys, true, guessedParseSetup);
  }

  // used to launch the AutoML asynchronously
  @Override
  public void run() {
    stopTimeMs = System.currentTimeMillis() + Math.round(1000 * buildSpec.build_control.stopping_criteria.max_runtime_secs());
    try {
      learn();
    } catch (AutoMLDoneException e) {
      // pass :)
    }
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
    long remaining = getStopTimeMs() - System.currentTimeMillis();
    return Math.max(0, remaining);
  }

  public int remainingModels() {
    if (buildSpec.build_control.stopping_criteria.max_models() == 0)
      return Integer.MAX_VALUE;
    return buildSpec.build_control.stopping_criteria.max_models() - modelCount.get();
  }

  @Override
  public boolean keepRunning() {
    return timeRemainingMs() > 0 && remainingModels() > 0;
  }

  private enum JobType {
    Unknown,
    ModelBuild,
    HyperparamSearch
  }

  public void pollAndUpdateProgress(Stage stage, String name, long workContribution, Job parentJob, Job subJob, JobType subJobType) {
    if (null == subJob) {
      parentJob.update(workContribution, "SKIPPED: " + name);
      return;
    }
    userFeedback.info(stage, name + " started");
    jobs.add(subJob);

    long lastWorkedSoFar = 0;
    long cumulative = 0;
    int gridLastCount = 0;

    while (subJob.isRunning()) {
      long workedSoFar = Math.round(subJob.progress() * workContribution);
      cumulative += workedSoFar;

      parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), name);

      if (JobType.HyperparamSearch == subJobType) {
        Grid grid = (Grid)subJob._result.get();
        int gridCount = grid.getModelCount();
        if (gridCount > gridLastCount) {
          userFeedback.info(Stage.ModelTraining,
                  "Built: " + gridCount + " models for search: " + name);
          this.addModels(grid.getModelKeys());
          gridLastCount = gridCount;
        }
      }

      try {
        Thread.currentThread().sleep(1000);
      }
      catch (InterruptedException e) {
        // keep going
      }
      lastWorkedSoFar = workedSoFar;
    }

    // pick up any stragglers:
    if (JobType.HyperparamSearch == subJobType) {
      Grid grid = (Grid)subJob._result.get();
      int gridCount = grid.getModelCount();
      if (gridCount > gridLastCount) {
        userFeedback.info(Stage.ModelTraining,
                "Built: " + gridCount + " models for search: " + name);
        this.addModels(grid.getModelKeys());
        gridLastCount = gridCount;
      }
    } else if (JobType.ModelBuild == subJobType) {
      this.addModel((Model)subJob._result.get());
    }

    // add remaining work
    parentJob.update(workContribution - lastWorkedSoFar);

    userFeedback.info(stage, name + " complete");
    try { jobs.remove(subJob); } catch (NullPointerException npe) {} // stop() can null jobs; can't just do a pre-check, because there's a race

  }

  private int individualModelsTrained = 0;
  /**
   * Helper for hex.ModelBuilder.
   * @return
   */
  public Job trainModel(Key<Model> key, String algoURLName, Model.Parameters parms) {
    String algoName = ModelBuilder.algoName(algoURLName);
    if (null == key) key = ModelBuilder.defaultKey(algoName);
    Job job = new Job<>(key,ModelBuilder.javaName(algoURLName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoURLName, job, key);
    Model.Parameters defaults = builder._parms;
    builder._parms = parms;

    setCommonModelBuilderParams(builder._parms);

    if (builder._parms._max_runtime_secs == 0)
      builder._parms._max_runtime_secs = Math.round(timeRemainingMs() / 1000.0);
    else
      builder._parms._max_runtime_secs = Math.min(builder._parms._max_runtime_secs,
                                         Math.round(timeRemainingMs() / 1000.0));

    // If we have set a seed for the search and not for the individual model params
    // then use a sequence starting with the same seed given for the model build.
    // Don't use the same exact seed so that, e.g., if we build two GBMs they don't
    // do the same row and column sampling.
    if (builder._parms._seed == defaults._seed && buildSpec.build_control.stopping_criteria.seed() != -1)
      builder._parms._seed = buildSpec.build_control.stopping_criteria.seed() + individualModelsTrained++;

    // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
    if (builder._parms._stopping_metric == defaults._stopping_metric)
      builder._parms._stopping_metric = buildSpec.build_control.stopping_criteria.stopping_metric();
    if (builder._parms._stopping_rounds == defaults._stopping_rounds)
      builder._parms._stopping_rounds = buildSpec.build_control.stopping_criteria.stopping_rounds();
    if (builder._parms._stopping_tolerance == defaults._stopping_tolerance)
      builder._parms._stopping_tolerance = buildSpec.build_control.stopping_criteria.stopping_tolerance();

    builder.init(false);          // validate parameters

    // TODO: handle error_count and messages

    return builder.trainModel();
  }

  /**
   * Do a random hyperparameter search.  Caller must eventually do a <i>get()</i>
   * on the returned Job to ensure that it's complete.
   * @param algoName
   * @param baseParms
   * @param searchParms
   * @return the started hyperparameter search job
   */
  public Job<Grid> hyperparameterSearch(String algoName, Model.Parameters baseParms, Map<String, Object[]> searchParms) {
    setCommonModelBuilderParams(baseParms);

    if (remainingModels() <= 0) {
      userFeedback.info(Stage.ModelTraining,"AutoML: hit the max_models limit; skipping " + algoName + " hyperparameter search");
      return null;
    }

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = (HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria)buildSpec.build_control.stopping_criteria.clone();
    if (searchCriteria.max_runtime_secs() == 0)
      searchCriteria.set_max_runtime_secs(this.timeRemainingMs() / 1000.0);
    else
      searchCriteria.set_max_runtime_secs(Math.min(searchCriteria.max_runtime_secs(),
              timeRemainingMs() / 1000.0));

    if (searchCriteria.max_models() == 0)
      searchCriteria.set_max_models(remainingModels());
    else
      searchCriteria.set_max_models(Math.min(searchCriteria.max_models(),
              remainingModels()));

    if (searchCriteria.max_runtime_secs() <= 0.001) {
      userFeedback.info(Stage.ModelTraining,"AutoML: out of time; skipping " + algoName + " hyperparameter search");
      return null;
    }
    userFeedback.info(Stage.ModelTraining, "AutoML: starting " + algoName + " hyperparameter search");

    // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
    Model.Parameters defaults;
    try {
      defaults = baseParms.getClass().newInstance();
    }
    catch (Exception e) {
      userFeedback.warn(Stage.ModelTraining, "Internal error doing hyperparameter search");
      throw new H2OIllegalArgumentException("Hyperparameter search can't create a new instance of Model.Parameters subclass: " + baseParms.getClass());
    }

    if (baseParms._stopping_metric == defaults._stopping_metric)
      baseParms._stopping_metric = buildSpec.build_control.stopping_criteria.stopping_metric();
    if (baseParms._stopping_rounds == defaults._stopping_rounds)
      baseParms._stopping_rounds = buildSpec.build_control.stopping_criteria.stopping_rounds();
    if (baseParms._stopping_tolerance == defaults._stopping_tolerance)
      baseParms._stopping_tolerance = buildSpec.build_control.stopping_criteria.stopping_tolerance();


    // NOTE:
    // RandomDiscrete Hyperparameter Search matches the logic used in #trainModel():
    // If we have set a seed for the search and not for the individual model params
    // then use a sequence starting with the same seed given for the model build.
    // Don't use the same exact seed so that, e.g., if we build two GBMs they don't
    // do the same row and column sampling.
    gridKey = Key.make(algoName + "_grid_" + this._key.toString());
    Job<Grid> gridJob = GridSearch.startGridSearch(gridKey,
            baseParms,
            searchParms,
            new GridSearch.SimpleParametersBuilderFactory(),
            searchCriteria);

    return gridJob;
  }


  private void setCommonModelBuilderParams(Model.Parameters params) {
    params._train = trainingFrame._key;
    if (null != validationFrame)
      params._valid = validationFrame._key;
    params._response_column = buildSpec.input_spec.response_column;
    params._ignored_columns = buildSpec.input_spec.ignored_columns;

    // currently required, for the base_models, for stacking:
    if (! (params instanceof StackedEnsembleModel.StackedEnsembleParameters)) {
      params._nfolds = 5;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      params._keep_cross_validation_predictions = true;
    }
  }

  private boolean exceededSearchLimits(String whatWeAreSkipping) {
    if (timeRemainingMs() <= 0.001) {
      userFeedback.info(Stage.ModelTraining, "AutoML: out of time; skipping " + whatWeAreSkipping);
      return true;
    }

    if (remainingModels() <= 0) {
      userFeedback.info(Stage.ModelTraining, "AutoML: hit the max_models limit; skipping " + whatWeAreSkipping);
      return true;
    }
    return false;
  }

  Job<DRFModel>defaultRandomForest() {
    if (exceededSearchLimits("DRF")) return null;

    DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
    setCommonModelBuilderParams(drfParameters);

    drfParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();

    Job randomForestJob = trainModel(null, "drf", drfParameters);
    return randomForestJob;
  }


  Job<DRFModel>defaultExtremelyRandomTrees() {
    if (exceededSearchLimits("XRT")) return null;

    DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
    setCommonModelBuilderParams(drfParameters);

    drfParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.Random;

    drfParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();

    Job randomForestJob = trainModel(ModelBuilder.defaultKey("XRT"), "drf", drfParameters);
    return randomForestJob;
  }

  public Job<Grid> defaultSearchGLM() {
    ///////////////////////////////////////////////////////////
    // do a random hyperparameter search with GLM
    ///////////////////////////////////////////////////////////
    // TODO: convert to using the REST API
    Key<Grid> gridKey = Key.make("GLM_grid_default_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    GLMModel.GLMParameters glmParameters = new GLMModel.GLMParameters();
        setCommonModelBuilderParams(glmParameters);

    glmParameters._lambda_search = true;
    glmParameters._family = getResponseColumn().isBinary() ? GLMModel.GLMParameters.Family.binomial :
            getResponseColumn().isCategorical() ? GLMModel.GLMParameters.Family.multinomial :
                    GLMModel.GLMParameters.Family.gaussian;  // TODO: other continuous distributions!

    Map<String, Object[]> searchParams = new HashMap<>();
    glmParameters._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};  // Note: standard GLM parameter is an array; don't use searchParams!
    searchParams.put("_missing_values_handling", new DeepLearningModel.DeepLearningParameters.MissingValuesHandling[] {DeepLearningModel.DeepLearningParameters.MissingValuesHandling.MeanImputation, DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip});

    Job<Grid>glmJob = hyperparameterSearch("GLM", glmParameters, searchParams);
    return glmJob;
  }

  public Job<Grid> defaultSearchGBM() {
    ///////////////////////////////////////////////////////////
    // do a random hyperparameter search with GBM
    ///////////////////////////////////////////////////////////
    // TODO: convert to using the REST API
    Key<Grid> gridKey = Key.make("GBM_grid_default_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    setCommonModelBuilderParams(gbmParameters);

    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    Map<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("_ntrees", new Integer[]{10000});
    searchParams.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
    searchParams.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
    searchParams.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
    searchParams.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
    searchParams.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0});
    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0});
    searchParams.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

/*
    if (trainingFrame.numCols() > 1000 && responseVec.isCategorical() && responseVec.cardinality() > 2)
      searchParams.put("col_sample_rate_per_tree", new Double[]{0.4, 0.6, 0.8, 1.0});
*/

    Job<Grid>gbmJob = hyperparameterSearch("GBM", gbmParameters, searchParams);
    return gbmJob;
  }

  public Job<Grid> defaultSearchDL1() {
    ///////////////////////////////////////////////////////////
    // do a random hyperparameter search with DL
    ///////////////////////////////////////////////////////////
    // TODO: convert to using the REST API
    Key<Grid> gridKey = Key.make("DL_grid_default_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    DeepLearningModel.DeepLearningParameters dlParameters = new DeepLearningModel.DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);

    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });

    // unique:
    searchParams.put("_hidden", new Integer[][] { {50}, {200}, {500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0 }, { 0.1 }, { 0.2 }, { 0.3 }, { 0.4 }, { 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch("DL", dlParameters, searchParams);
    return dlJob;
  }

  public Job<Grid> defaultSearchDL2() {
    ///////////////////////////////////////////////////////////
    // do a random hyperparameter search with DL
    ///////////////////////////////////////////////////////////
    // TODO: convert to using the REST API
    Key<Grid> gridKey = Key.make("DL_grid_default_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    DeepLearningModel.DeepLearningParameters dlParameters = new DeepLearningModel.DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);

    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });

    // unique:
    searchParams.put("_hidden", new Integer[][] { {50, 50}, {200, 200}, {500, 500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0, 0.0 }, { 0.1, 0.1 }, { 0.2, 0.2 }, { 0.3, 0.3 }, { 0.4, 0.4 }, { 0.5, 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch("DL", dlParameters, searchParams);
    return dlJob;
  }

  public Job<Grid> defaultSearchDL3() {
    ///////////////////////////////////////////////////////////
    // do a random hyperparameter search with DL
    ///////////////////////////////////////////////////////////
    // TODO: convert to using the REST API
    Key<Grid> gridKey = Key.make("DL_grid_default_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    DeepLearningModel.DeepLearningParameters dlParameters = new DeepLearningModel.DeepLearningParameters();
    setCommonModelBuilderParams(dlParameters);

    dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
    dlParameters._adaptive_rate = true;
    dlParameters._activation = RectifierWithDropout;

    Map<String, Object[]> searchParams = new HashMap<>();
    // common:
    searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
    searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
    searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });

    // unique:
    searchParams.put("_hidden", new Integer[][] { {50, 50, 50}, {200, 200, 200}, {500, 500, 500} });
    searchParams.put("_hidden_dropout_ratios", new Double[][] { { 0.0, 0.0, 0.0 }, { 0.1, 0.1, 0.1 }, { 0.2, 0.2, 0.2 }, { 0.3, 0.3, 0.3 }, { 0.4, 0.4, 0.4 }, { 0.5, 0.5, 0.5 } });

    Job<Grid>dlJob = hyperparameterSearch("DL", dlParameters, searchParams);
    return dlJob;
  }

  Job<StackedEnsembleModel>stack(Key<Model>[]... modelKeyArrays) {
    List<Key<Model>> allModelKeys = new ArrayList<>();
    for (Key<Model>[] modelKeyArray : modelKeyArrays)
      allModelKeys.addAll(Arrays.asList(modelKeyArray));

    StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters();
    stackedEnsembleParameters._base_models = allModelKeys.toArray(new Key[0]);
    stackedEnsembleParameters._selection_strategy = StackedEnsembleModel.StackedEnsembleParameters.SelectionStrategy.choose_all;
    Job ensembleJob = trainModel(null, "stackedensemble", stackedEnsembleParameters);
    return ensembleJob;
  }

  Job<DeepWaterModel>defaulDeepWater() {
    if (exceededSearchLimits("DeepWater")) return null;

    DeepWaterParameters deepWaterParameters = new DeepWaterParameters();
    setCommonModelBuilderParams(deepWaterParameters);

    deepWaterParameters._stopping_tolerance = this.buildSpec.build_control.stopping_criteria.stopping_tolerance();

    Job deepWaterJob = trainModel(null, "deepwater", deepWaterParameters);
    return deepWaterJob;
  }

  // manager thread:
  //  1. Do extremely cursory pass over data and gather only the most basic information.
  //
  //     During this pass, AutoML will learn how timely it will be to do more info
  //     gathering on _fr. There's already a number of interesting stats available
  //     thru the rollups, so no need to do too much too soon.
  //
  //  2. Build a very dumb RF (with stopping_rounds=1, stopping_tolerance=0.01)
  //
  //  3. TODO: refinement passes and strategy selection
  //
  public void learn() {
    userFeedback.info(Stage.Workflow, "AutoML build started: " + fullTimestampFormat.format(new Date()));

    ///////////////////////////////////////////////////////////
    // gather initial frame metadata and guess the problem type
    ///////////////////////////////////////////////////////////

    // TODO: Nishant says sometimes frameMetadata is null, so maybe we need to wait for it?
    // null FrameMetadata arises when delete() is called without waiting for start() to finish.
    frameMetadata = new FrameMetadata(userFeedback, trainingFrame,
            trainingFrame.find(buildSpec.input_spec.response_column),
            trainingFrame._key.toString()).computeFrameMetaPass1();

    HashMap<String, Object> frameMeta = FrameMetadata.makeEmptyFrameMeta();
    frameMetadata.fillSimpleMeta(frameMeta);
    giveDatasetFeedback(trainingFrame, userFeedback, frameMeta);

    job.update(20, "Computed dataset metadata");

    isClassification = frameMetadata.isClassification();

    ///////////////////////////////////////////////////////////
    // build a fast RF with default settings...
    ///////////////////////////////////////////////////////////
    Job<DRFModel>defaultRandomForestJob = defaultRandomForest();
    pollAndUpdateProgress(Stage.ModelTraining, "Default Random Forest build", 50, this.job(), defaultRandomForestJob, JobType.ModelBuild);


    ///////////////////////////////////////////////////////////
    // ... and another with "XRT" / extratrees settings
    ///////////////////////////////////////////////////////////
    Job<DRFModel>defaultExtremelyRandomTreesJob = defaultExtremelyRandomTrees();
    pollAndUpdateProgress(Stage.ModelTraining, "Default Extremely Random Trees (XRT) build", 50, this.job(), defaultExtremelyRandomTreesJob, JobType.ModelBuild);


    ///////////////////////////////////////////////////////////
    // build GLMs with the default search parameters
    ///////////////////////////////////////////////////////////
    // TODO: run for only part of the remaining time?
    Job<Grid>glmJob = defaultSearchGLM();
    pollAndUpdateProgress(Stage.ModelTraining, "GLM hyperparameter search", 50, this.job(), glmJob, JobType.HyperparamSearch);

    // TODO: build GBMs with Arno's default settings, using 1-grid Cartesian searches
    // into the same grid object as the search below.
    // Can't do until PUBDEV-4361 is fixed.

    ///////////////////////////////////////////////////////////
    // build GBMs with the default search parameters
    ///////////////////////////////////////////////////////////
    // TODO: run for only part of the remaining time?
    Job<Grid> gbmJob = defaultSearchGBM();
    pollAndUpdateProgress(Stage.ModelTraining, "GBM hyperparameter search", 150, this.job(), gbmJob, JobType.HyperparamSearch);

    ///////////////////////////////////////////////////////////
    // build DL models with the default search parameter set 1
    ///////////////////////////////////////////////////////////
    // TODO: run for only part of the remaining time?
    Job<Grid>dlJob1 = defaultSearchDL1();
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 1", 150, this.job(), dlJob1, JobType.HyperparamSearch);


    ///////////////////////////////////////////////////////////
    // build DL models with the default search parameter set 2
    ///////////////////////////////////////////////////////////
    // TODO: run for only part of the remaining time?
    Job<Grid>dlJob2 = defaultSearchDL2();
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 2", 200, this.job(), dlJob2, JobType.HyperparamSearch);


    ///////////////////////////////////////////////////////////
    // build DL models with the default search parameter set 3
    ///////////////////////////////////////////////////////////
    // TODO: run for only part of the remaining time?
    Job<Grid>dlJob3 = defaultSearchDL3();
    pollAndUpdateProgress(Stage.ModelTraining, "DeepLearning hyperparameter search 3", 300, this.job(), dlJob3, JobType.HyperparamSearch);

    ///////////////////////////////////////////////////////////
    // build a DeepWater model
    ///////////////////////////////////////////////////////////
    if (DeepWater.haveBackend()) {
      Job<DeepWaterModel> defaultDeepWaterJob = defaulDeepWater();
      pollAndUpdateProgress(Stage.ModelTraining, "Default DeepWater build", 50, this.job(), defaultDeepWaterJob, JobType.ModelBuild);
    }


    ///////////////////////////////////////////////////////////
    // (optionally) build StackedEnsemble
    ///////////////////////////////////////////////////////////
    Model[] allModels = leaderboard().getModels();

    if (allModels.length == 0){
      this.job.update(50, "No models built: StackedEnsemble build skipped");
      userFeedback.info(Stage.ModelTraining, "No models were built, due to timeouts.");
    } else {
      Model m = allModels[0];
      if (m._output.isClassifier() && !m._output.isBinomialClassifier()) {
        // nada
        this.job.update(50, "Multinomial classifier: StackedEnsemble build skipped");
        userFeedback.info(Stage.ModelTraining,"Multinomial classifier: StackedEnsemble build skipped");
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

        Job<StackedEnsembleModel> ensembleJob = stack(notEnsembles);
        pollAndUpdateProgress(Stage.ModelTraining, "StackedEnsemble build", 50, this.job(), ensembleJob, JobType.ModelBuild);
      }
    }
    userFeedback.info(Stage.Workflow, "AutoML: build done; built " + modelCount + " models");
    Log.info(userFeedback.toString("User Feedback for AutoML Run " + this._key));
    Log.info();

    Leaderboard trainingLeaderboard = new Leaderboard(project() + "_training", userFeedback, this.trainingFrame);
    trainingLeaderboard.addModels(this.leaderboard.getModelKeys());
    Log.info(trainingLeaderboard.toTwoDimTable("TRAINING FRAME Leaderboard for project " + project(), true).toString());
    Log.info();

    Leaderboard validationLeaderboard = new Leaderboard(project() + "_validation", userFeedback, this.validationFrame);
    validationLeaderboard.addModels(this.leaderboard.getModelKeys());
    Log.info(validationLeaderboard.toTwoDimTable("VALIDATION FRAME Leaderboard for project " + project(), true).toString());
    Log.info();

    Log.info(leaderboard.toTwoDimTable("Leaderboard for project " + project(), true).toString());

    possiblyVerifyImmutability();
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
    // TODO: name this job better
    AutoML aml = AutoML.makeAutoML(Key.<AutoML>make(), buildSpec);
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
      Job job = new /* Timed */ H2OJob(aml, aml._key, aml.timeRemainingMs()).start();
      aml.job = job;
      // job._max_runtime_msecs = Math.round(1000 * aml.buildSpec.build_control.stopping_criteria.max_runtime_secs());

      // job work:
      // import/parse (30), Frame metadata (20), GBM grid (900), StackedEnsemble (50)
      job._work = 1000;
      DKV.put(aml);

      job.update(30, "Data import and parse complete");
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
  public void delete() {
    //if (frameMetadata != null) frameMetadata.delete(); //TODO: We shouldn't have to worry about FrameMetadata being null
    AutoMLUtils.cleanup_adapt(trainingFrame, origTrainingFrame);
    leaderboard.delete();
    userFeedback.delete();
    remove();
  }

  /**
   * Same as delete() but also deletes all Objects made from this instance.
   */
  public void deleteWithChildren() {
    leaderboard.deleteWithChildren();
    // implicit: feedback.delete();
    delete(); // is it safe to do leaderboard.delete() now?
    if (gridKey != null) gridKey.remove();

    // If the Frame was made here (e.g. buildspec contained a path, then it will be deleted
    if (buildSpec.input_spec.training_frame == null) {
      origTrainingFrame.delete();
    }
    if (buildSpec.input_spec.validation_frame == null && buildSpec.input_spec.validation_path != null) {
      validationFrame.delete();
    }
  }

  /*
  private ModelBuilder selectInitial(FrameMetadata fm) {  // may use _isClassification so not static method
    // TODO: handle validation frame if present
    Frame[] trainTest = AutoMLUtils.makeTrainTestFromWeight(fm._fr, fm.weights());
    ModelBuilder mb = InitModel.initRF(trainTest[0], trainTest[1], fm.response()._name);
    mb._parms._ignored_columns = fm.ignoredCols();
    return mb;
  }
  */

  public Job job() {
    if (null == this.job) return null;
    return DKV.getGet(this.job._key);
  }

  public Leaderboard leaderboard() { return leaderboard._key.get(); }
  public Model leader() { return (leaderboard == null ? null : leaderboard().getLeader()); }

  public UserFeedback userFeedback() { return userFeedback._key.get(); }

  public String project() {
    return buildSpec.project();
  }

  public void addModels(final Key<Model>[] newModels) {
    modelCount.addAndGet(newModels.length);
    leaderboard.addModels(newModels);
  }

  public void addModel(final Key<Model> newModel) {
    modelCount.addAndGet(1);
    leaderboard.addModel(newModel);
  }

  public void addModel(final Model newModel) {
    modelCount.addAndGet(1);
    leaderboard.addModel(newModel);
  }

  // satisfy typing for job return type...
  public static class AutoMLKeyV3 extends KeyV3<Iced, AutoMLKeyV3, AutoML> {
    public AutoMLKeyV3() {
    }

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

  public boolean possiblyVerifyImmutability() {
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
}
