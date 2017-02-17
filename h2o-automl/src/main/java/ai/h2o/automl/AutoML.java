package ai.h2o.automl;

import ai.h2o.automl.strategies.initial.InitModel;
import ai.h2o.automl.utils.AutoMLUtils;
import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper;
import hex.StackedEnsembleModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.tree.SharedTreeModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.*;
import water.api.schemas3.ImportFilesV3;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.util.*;

import static water.Key.make;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing concurrent
 * strategies of execution in an effort to discover an optimal supervised model for some
 * given (dataset, response, loss) combo.
 */
public final class AutoML extends Lockable<AutoML> implements TimedH2ORunnable {

  private final boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with

  private AutoMLBuildSpec buildSpec;     // all parameters for doing this AutoML build
  private Frame origTrainingFrame;       // untouched original training frame

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

  private Frame trainingFrame;           // munged training frame: can add and remove Vecs, but not mutate Vec data in place
  private Frame validationFrame;         // optional validation frame
  private Vec responseColumn;
  FrameMetadata frameMetadata;           // metadata for trainingFrame

  // TODO: remove dead code
  private Key<Grid> gridKey;             // Grid key from GridSearch
  private boolean isClassification;

  private long stopTimeMs;
  private Job job;                  // the Job object for the build of this AutoML.  TODO: can we have > 1?

  // TODO: make non-transient
  private transient ArrayList<Job> jobs;

  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  private String project;

  private Leaderboard leaderboard;

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

    this.buildSpec = buildSpec;

    this.origTrainingFrame = DKV.getGet(buildSpec.input_spec.training_frame);
    this.validationFrame = DKV.getGet(buildSpec.input_spec.validation_frame);

    if (null == buildSpec.input_spec.training_frame && null != buildSpec.input_spec.training_path)
      this.origTrainingFrame = importParseFrame(buildSpec.input_spec.training_path, buildSpec.input_spec.parse_setup);
    if (null == buildSpec.input_spec.validation_frame && null != buildSpec.input_spec.validation_path)
      this.validationFrame = importParseFrame(buildSpec.input_spec.validation_path, buildSpec.input_spec.parse_setup);

    if (null == this.origTrainingFrame)
      throw new IllegalArgumentException("No training frame; user specified training_path: " +
              buildSpec.input_spec.training_path +
              " and training_frame: " + buildSpec.input_spec.training_frame);

    this.trainingFrame = new Frame(origTrainingFrame);
    DKV.put(this.trainingFrame);

    this.responseColumn = trainingFrame.vec(buildSpec.input_spec.response_column);

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      originalTrainingFrameVecs = origTrainingFrame.vecs().clone();
      originalTrainingFrameNames = origTrainingFrame.names().clone();
      originalTrainingFrameChecksums = new long[originalTrainingFrameVecs.length];

      for (int i = 0; i < originalTrainingFrameVecs.length; i++)
        originalTrainingFrameChecksums[i] = originalTrainingFrameVecs[i].checksum();
    }

    // TODO: allow the user to set the project via the buildspec
    String[] path = this.origTrainingFrame._key.toString().split("/");
    project = path[path.length - 1]
      .replace(".hex", "")
      .replace("CSV", "")
      .replace("XLS", "")
      .replace("XSLX", "")
      .replace("SVMLight", "")
      .replace("ARFF", "")
      .replace("ORC", "")
      .replace("csv", "")
      .replace("xls", "")
      .replace("xslx", "")
      .replace("svmlight", "")
      .replace("arff", "")
      .replace("orc", "");
    leaderboard = new Leaderboard(project);

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
  }

/*
  public AutoML(Key<AutoML> key, String datasetName, Frame fr, Frame[] relations, String responseName, String loss, long maxTime,

                double minAccuracy, boolean ensemble, algo[] excludeAlgos, boolean tryMutations ) {
    this(key,datasetName,fr,relations,fr.find(responseName),loss,maxTime,minAccuracy,ensemble,excludeAlgos,tryMutations);
  }
  */

  public static AutoML makeAutoML(Key<AutoML> key, AutoMLBuildSpec buildSpec) {
    // if (buildSpec.input_spec.parse_setup == null)
    //   buildSpec.input_spec.parse_setup = ParseSetup.guessSetup(); // use defaults!

    AutoML autoML = new AutoML(key, buildSpec);

    if (null == autoML.trainingFrame)
      throw new IllegalArgumentException("No training data has been specified, either as a path or a key.");

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

  @Override
  public boolean keepRunning() {
    return timeRemainingMs() > 0;
  }

  public void pollAndUpdateProgress(String name, long workContribution, Job parentJob, Job subJob) {
    Log.info(name + " started");
    jobs.add(subJob);

    long lastWorkedSoFar = 0;
    long cumulative = 0;
    while (subJob.isRunning()) {
      long workedSoFar = Math.round(subJob.progress() * workContribution);
      cumulative += workedSoFar;

      parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), name);
      try {
        Thread.currentThread().sleep(1000);
      }
      catch (InterruptedException e) {
        // keep going
      }
      lastWorkedSoFar = workedSoFar;
    }

    // add remaining work
    parentJob.update(workContribution - lastWorkedSoFar);

    Log.info(name + " complete");
    try { jobs.remove(subJob); } catch (NullPointerException npe) {} // stop() can null jobs; can't just do a pre-check, because there's a race

  }

  /**
   * Helper for hex.ModelBuilder.
   * @return
   */
  public Job trainModel(Key<Model> key, String algoURLName, Model.Parameters parms) {
    String algoName = ModelBuilder.algoName(algoURLName);
    if (null == key) key = ModelBuilder.defaultKey(algoName);
    Job job = new Job<>(key,ModelBuilder.javaName(algoURLName), algoName);

    ModelBuilder builder = ModelBuilder.make(algoURLName, job, key);
    builder._parms = parms;
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
    Log.info("AutoML: starting " + algoName + " hyperparameter search");
    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = (HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria)buildSpec.build_control.stopping_criteria.clone();
    searchCriteria.set_max_runtime_secs(this.timeRemainingMs() / 1000.0);

    Job<Grid> gridJob = GridSearch.startGridSearch(gridKey,
            baseParms,
            searchParms,
            new GridSearch.SimpleParametersBuilderFactory(),
            buildSpec.build_control.stopping_criteria);

    return gridJob;
  }


  Job<DRFModel>defaultRandomForest() {
    DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
    drfParameters._train = trainingFrame._key;
    if (null != validationFrame)
      drfParameters._valid = validationFrame._key;
    drfParameters._response_column = buildSpec.input_spec.response_column;
    drfParameters._ignored_columns = buildSpec.input_spec.ignored_columns;

    // required for stacking:
    drfParameters._nfolds = 5;
    drfParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
    drfParameters._keep_cross_validation_predictions = true;

    Job randomForestJob = trainModel(null, "drf", drfParameters);
    return randomForestJob;
  }


  Job<DRFModel>defaultExtremelyRandomTrees() {
    DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
    drfParameters._train = trainingFrame._key;
    if (null != validationFrame)
      drfParameters._valid = validationFrame._key;
    drfParameters._response_column = buildSpec.input_spec.response_column;
    drfParameters._ignored_columns = buildSpec.input_spec.ignored_columns;
    drfParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.Random;

    // required for stacking:
    drfParameters._nfolds = 5;
    drfParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
    drfParameters._keep_cross_validation_predictions = true;

    Job randomForestJob = trainModel(ModelBuilder.defaultKey("XRT"), "drf", drfParameters);
    return randomForestJob;
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
    gbmParameters._train = trainingFrame._key;
    if (null != validationFrame)
      gbmParameters._valid = validationFrame._key;
    gbmParameters._response_column = buildSpec.input_spec.response_column;
    gbmParameters._ignored_columns = buildSpec.input_spec.ignored_columns;

    // required for stacking:
    gbmParameters._nfolds = 5;
    gbmParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
    gbmParameters._keep_cross_validation_predictions = true;

    gbmParameters._score_tree_interval = 5;

    // TODO: wire through from buildSpec
    gbmParameters._stopping_metric = ScoreKeeper.StoppingMetric.AUTO;
    gbmParameters._stopping_tolerance = 0.0001;
    gbmParameters._stopping_rounds = 3;
    gbmParameters._max_runtime_secs = this.timeRemainingMs() / 1000;  // TODO: run for only part of the remaining time?
    Log.info("About to run GBM for: " + gbmParameters._max_runtime_secs + "S");
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

  Job<StackedEnsembleModel>stack(Key<Model>[]... modelKeyArrays) {
    List<Key<Model>> allModelKeys = new ArrayList<>();
    for (Key<Model>[] modelKeyArray : modelKeyArrays)
      allModelKeys.addAll(Arrays.asList(modelKeyArray));

    StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters();
    stackedEnsembleParameters._base_models = allModelKeys.toArray(new Key[0]);
    stackedEnsembleParameters._selection_strategy = StackedEnsembleModel.StackedEnsembleParameters.SelectionStrategy.choose_all;
    stackedEnsembleParameters._train = trainingFrame._key;
    if (null != validationFrame)
      stackedEnsembleParameters._valid = validationFrame._key;
    stackedEnsembleParameters._response_column = buildSpec.input_spec.response_column;

    Job ensembleJob = trainModel(null, "stackedensemble", stackedEnsembleParameters);
    return ensembleJob;
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

    ///////////////////////////////////////////////////////////
    // gather initial frame metadata and guess the problem type
    ///////////////////////////////////////////////////////////

    // TODO: Nishant says sometimes frameMetadata is null, so maybe we need to wait for it?
    // null FrameMetadata arises when delete() is called without waiting for start() to finish.
    frameMetadata = new FrameMetadata(trainingFrame,
            trainingFrame.find(buildSpec.input_spec.response_column),
            trainingFrame.toString()).computeFrameMetaPass1();

    HashMap<String, Object> frameMeta = FrameMetadata.makeEmptyFrameMeta();
    frameMetadata.fillSimpleMeta(frameMeta);

    job.update(20, "Computed dataset metadata");

    isClassification = frameMetadata.isClassification();

    ///////////////////////////////////////////////////////////
    // build a fast RF with default settings...
    ///////////////////////////////////////////////////////////
    Job<DRFModel>defaultRandomForestJob = defaultRandomForest();

    pollAndUpdateProgress("Default Random Forest build", 50, this.job(), defaultRandomForestJob);

    DRFModel defaultDRF = (DRFModel)defaultRandomForestJob.get();
    leaderboard.addModel(defaultDRF);


    ///////////////////////////////////////////////////////////
    // ... and another with "XRT" / extratrees settings
    ///////////////////////////////////////////////////////////
    Job<DRFModel>defaultExtremelyRandomTreesJob = defaultExtremelyRandomTrees();

    pollAndUpdateProgress("Default Extremely Random Trees (XRT) build", 50, this.job(), defaultExtremelyRandomTreesJob);

    DRFModel defaultXRT = (DRFModel)defaultExtremelyRandomTreesJob.get();
    leaderboard.addModel(defaultXRT);




    ///////////////////////////////////////////////////////////
    // build GBMs with the default search parameters
    ///////////////////////////////////////////////////////////

    Job<Grid>gbmJob = defaultSearchGBM();
    pollAndUpdateProgress("GBM hyperparameter search", 800, this.job(), gbmJob);

    Grid gbmGrid = DKV.getGet(gbmJob._result);
    leaderboard.addModels(gbmGrid.getModelKeys());

    ///////////////////////////////////////////////////////////
    // (optionally) build StackedEnsemble
    ///////////////////////////////////////////////////////////

    Model m = gbmGrid.getModels()[0];
    if (m._output.isClassifier() && ! m._output.isBinomialClassifier()) {
      // nada
      this.job.update(100, "Multinomial classifier: StackedEnsemble build skipped");
      Log.info("Multinomial classifier: StackedEnsemble build skipped");
    } else {
      ///////////////////////////////////////////////////////////
      // stack all models
      ///////////////////////////////////////////////////////////

      // Also stack models from other AutoML runs, by using the Leaderboard! (but don't stack stacks)
      Model[] all = leaderboard().models();
      int nonEnsembleCount = 0;
      for (Model aModel : all)
        if (! (aModel instanceof StackedEnsembleModel))
          nonEnsembleCount++;

      Key<Model>[] notEnsembles = new Key[nonEnsembleCount];
      int notEnsembleIndex = 0;
      for (Model aModel : all)
        if (! (aModel instanceof StackedEnsembleModel))
          notEnsembles[notEnsembleIndex++] = aModel._key;

      Job<StackedEnsembleModel>ensembleJob = stack(notEnsembles);

      pollAndUpdateProgress("StackedEnsemble build", 100, this.job(), ensembleJob);

      StackedEnsembleModel ensemble = (StackedEnsembleModel)ensembleJob.get();
      leaderboard.addModel(ensemble);
    }

    Log.info("AutoML: build done");

    Log.info(leaderboard.toString("\n"));

    possiblyVerifyImmutability();
    // gather more data? build more models? start applying transforms? what next ...?
    stop();
  }

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
    remove();
  }

  /**
   * Same as delete() but also deletes all Objects made from this instance.
   */
  public void deleteWithChildren() {
    leaderboard.deleteWithChildren();
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

  private ModelBuilder selectInitial(FrameMetadata fm) {  // may use _isClassification so not static method
    // TODO: handle validation frame if present
    Frame[] trainTest = AutoMLUtils.makeTrainTestFromWeight(fm._fr, fm.weights());
    ModelBuilder mb = InitModel.initRF(trainTest[0], trainTest[1], fm.response()._name);
    mb._parms._ignored_columns = fm.ignoredCols();
    return mb;
  }


  // all model builds by AutoML call into this
  // TODO: who is the caller?!
  // TODO: remove this restriction!
  // expected to only ever have a single AutoML instance going at a time
  Model build(ModelBuilder mb) {
    Job j;
    if (null == jobs) throw new AutoMLDoneException();
    jobs.add(j = mb.trainModel());
    Model m = (Model) j.get();
    // save the weights, drop the frames!

    // TODO: don't munge the original Frame!
    trainingFrame.remove("weight");
    trainingFrame.delete();
    if (null != validationFrame) {
      validationFrame.remove("weight");
      validationFrame.delete();
    }
    if (null == jobs) throw new AutoMLDoneException();
    jobs.remove(j);
    leaderboard.addModel(m);
    return m;
  }

  public Job job() {
    if (null == this.job) return null;
    return DKV.getGet(this.job._key);
  }

  public Leaderboard leaderboard() { return leaderboard._key.get(); }


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
      Log.info("Verifying training frame immutability. . .");

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

      Log.info(". . . training frame was mutated: " + warning);
    } else {
      Log.info("Not verifying training frame immutability. . .  This is turned off for efficiency.");
    }

    return warning;
  }

}
