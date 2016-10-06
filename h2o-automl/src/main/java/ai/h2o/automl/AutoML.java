package ai.h2o.automl;

import ai.h2o.automl.strategies.initial.InitModel;
import ai.h2o.automl.utils.AutoMLUtils;
import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import water.*;
import water.api.GridSearchHandler.DefaultModelParametersBuilderFactory;
import water.api.schemas3.ImportFilesV3;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static water.Key.make;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing concurrent
 * strategies of execution in an effort to discover an optimal supervised model for some
 * given (dataset, response, loss) combo.
 */
public final class AutoML extends Keyed<AutoML> implements TimedH2ORunnable {

  private final boolean verifyImmutability = true; // check that trainingFrame hasn't been messed with

  private AutoMLBuildSpec buildSpec;     // all parameters for doing this AutoML build
  private Frame origTrainingFrame;       // untouched original training frame
  private Frame trainingFrame;           // munged training frame: can add and remove Vecs, but not mutate Vec data in place
  private Frame validationFrame;         // optional validation frame
  private Vec responseVec;
  FrameMetadata frameMetadata;           // metadata for trainingFrame
  private boolean isClassification;

  private long timeRemaining;
  private long totalTime;
  private Job job;                  // the Job object for the build of this AutoML.  TODO: can we have > 1?
  private transient ArrayList<Job> jobs;

  // check that we haven't messed up the original Frame
  private Vec[] originalTrainingFrameVecs;
  private String[] originalTrainingFrameNames;
  private long[] originalTrainingFrameChecksums;


  // TODO: UGH: this should be dynamic, and it's easy to make it so
  public enum algo {
    RF, GBM, GLM, GLRM, DL, KMEANS
  }  // consider EnumSet

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

    this.responseVec = trainingFrame.vec(buildSpec.input_spec.response_column);

    if (verifyImmutability) {
      // check that we haven't messed up the original Frame
      originalTrainingFrameVecs = origTrainingFrame.vecs().clone();
      originalTrainingFrameNames = origTrainingFrame.names().clone();
      originalTrainingFrameChecksums = new long[originalTrainingFrameVecs.length];

      for (int i = 0; i < originalTrainingFrameVecs.length; i++)
        originalTrainingFrameChecksums[i] = originalTrainingFrameVecs[i].checksum();
    }

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

    H2O.getPM().importFiles(importFiles.path, files, keys, fails, dels);

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
    totalTime = System.currentTimeMillis() + Math.round(1000 * buildSpec.build_control.stopping_criteria.max_runtime_secs());
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
    totalTime = -1;
    timeRemaining = -1;
    jobs = null;
  }

  @Override
  public boolean keepRunning() {
    return (timeRemaining = totalTime - System.currentTimeMillis()) > 0;
  }

  @Override
  public long timeRemaining() {
    return timeRemaining;
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

    // step 1: gather initial frame metadata and guess the problem type
    frameMetadata = new FrameMetadata(trainingFrame,
            trainingFrame.find(buildSpec.input_spec.response_column),
            trainingFrame.toString()).computeFrameMetaPass1();
    isClassification = frameMetadata.isClassification();

    // step 2: build a fast RF
    // ModelBuilder initModel = selectInitial(frameMetadata);
    // Model m = build(initModel); // need to track this...

    // step 2 for AutoML phase 1: do a random hyperparameter search with GBM
    Key<Grid> gridKey = Key.make("grid_0_" + this._key.toString());

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = buildSpec.build_control.stopping_criteria;

    // TODO: put this into a Provider, which can return multiple searches
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    gbmParameters._train = trainingFrame._key;
    if (null != validationFrame)
      gbmParameters._valid = validationFrame._key;
    gbmParameters._response_column = buildSpec.input_spec.response_column;

    gbmParameters._nfolds = 5;
    gbmParameters._score_tree_interval = 20;
    gbmParameters._stopping_metric = ScoreKeeper.StoppingMetric.AUTO;
    gbmParameters._stopping_tolerance = 0.001;
    gbmParameters._stopping_rounds = 2;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    Map<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("ntrees", new Integer[]{10000});
    searchParams.put("max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
    searchParams.put("min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
    searchParams.put("learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
    searchParams.put("sample_rate", new Double[]{0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.00});
    searchParams.put("col_sample_rate_per_tree", new Double[]{0.6, 1.00});
    searchParams.put("min_split_improvement", new Double[]{1e-4, 1e-5});

    if (trainingFrame.numCols() > 1000 && responseVec.isCategorical() && responseVec.cardinality() > 2)
      searchParams.put("col_sample_rate_per_tree", new Double[]{0.4, 0.6, 0.8, 1.0});

    Log.info("AutoML: starting hyperparameter search");

    Job<Grid> gridJob = GridSearch.startGridSearch(gridKey,
            gbmParameters, // TODO
            searchParams, // TODO
            new DefaultModelParametersBuilderFactory<Model.Parameters, ModelParametersSchemaV3>(),
            searchCriteria);

    // TODO: poll!  (better than this)
    while (gridJob.isRunning())
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
    ;
    // TODO:
    this.job.update(1, "grid search complete");

    Log.info("AutoML: build done");
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

    Job job = new TimedH2OJob(aml, aml._key).start();
    aml.job = job;
    job._max_runtime_msecs = Math.round(1000 * buildSpec.build_control.stopping_criteria.max_runtime_secs());
    job._work = 3; // import, feature generation, grid search

    aml.job.update(1, "data import complete");

    DKV.put(aml);
    return aml;
  }

  public Key<Model> getLeaderKey() {
    final Value val = DKV.get(LEADER);
    if (val == null) return null;
    ModelLeader ml = val.get();
    return ml._leader;
  }

  public void delete() {
    trainingFrame.delete();  // TODO: no
    frameMetadata.delete();
    for (Model m : models()) m.delete();
    DKV.remove(MODELLIST);
    DKV.remove(LEADER);
  }

  private ModelBuilder selectInitial(FrameMetadata fm) {  // may use _isClassification so not static method
    // TODO: handle validation frame if present
    Frame[] trainTest = AutoMLUtils.makeTrainTestFromWeight(fm._fr, fm.weights());
    ModelBuilder mb = InitModel.initRF(trainTest[0], trainTest[1], fm.response()._name);
    mb._parms._ignored_columns = fm.ignoredCols();
    return mb;
  }

  // track models built by automl
  public final Key<Model> MODELLIST = make("AutoMLModelList" + make().toString(), (byte) 0, (byte) 2 /*builtin key*/, false);  // public for the test

  class ModelList extends Keyed {
    Key<Model>[] _models;

    ModelList() {
      super(MODELLIST);
      _models = new Key[0];
    }

    @Override
    protected long checksum_impl() {
      throw H2O.fail("no such method for ModelList");
    }
  }

  Model[] models() {
    final Value val = DKV.get(MODELLIST);
    if (val == null) return new Model[0];
    ModelList ml = val.get();
    Model[] models = new Model[ml._models.length];
    int j = 0;
    for (int i = 0; i < ml._models.length; i++) {
      final Value model = DKV.get(ml._models[i]);
      if (model != null) models[j++] = model.get();
    }
    assert j == models.length; // All models still exist
    return models;
  }

  public final Key<Model> LEADER = make("AutoMLModelLeader" + make().toString(), (byte) 0, (byte) 2, false);

  class ModelLeader extends Keyed {
    Key<Model> _leader;

    ModelLeader() {
      super(LEADER);
      _leader = null;
    }

    @Override
    protected long checksum_impl() {
      throw H2O.fail("no such method for ModelLeader");
    }
  }

  Model leader() {
    final Value val = DKV.get(LEADER);
    if (val == null) return null;
    ModelLeader ml = val.get();
    final Value leaderModelKey = DKV.get(ml._leader);
    assert null != leaderModelKey; // if the LEADER is in the DKV, then there better be a model!
    return leaderModelKey.get();
  }

  private void updateLeader(Model m) {
    Model leader = leader();
    final Key leaderKey;
    if (leader == null) leaderKey = m._key;
    else {
      // compare leader to m; get the key that minimizes this._loss
      leaderKey = leader._key;
    }

    // update the leader if needed
    if (leader == null || leaderKey.equals(leader._key)) {
      new TAtomic<ModelLeader>() {
        @Override
        public ModelLeader atomic(ModelLeader old) {
          if (old == null) old = new ModelLeader();
          old._leader = leaderKey;
          return old;
        }
      }.invoke(LEADER);
    }
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
    final Key<Model> modelKey = m._key;
    new TAtomic<ModelList>() {
      @Override
      public ModelList atomic(ModelList old) {
        if (old == null) old = new ModelList();
        Key<Model>[] models = old._models;
        old._models = Arrays.copyOf(models, models.length + 1);
        old._models[models.length] = modelKey;
        return old;
      }
    }.invoke(MODELLIST);

    updateLeader(m);
    return m;
  }

  public Job job() {
    if (null == this.job) return null;
    return DKV.getGet(this.job._key);
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
