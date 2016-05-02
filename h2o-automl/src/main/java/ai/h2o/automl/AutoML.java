package ai.h2o.automl;

import ai.h2o.automl.strategies.initial.InitModel;
import hex.Model;
import hex.ModelBuilder;
import water.*;
import water.api.KeyV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.util.IcedHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing concurrent
 * strategies of execution in an effort to discover an optimal supervised model for some
 * given (dataset, response, loss) combo.
 */
public final class AutoML extends Keyed<AutoML> implements TimedH2ORunnable {

  private final String _datasetName;     // dataset name
  private final Frame _fr;               // all learning on this frame
  private final int _response;           // response column, -1 for no response column
  private final String _loss;            // overarching loss to minimize (meta loss)
  private final long _maxTime;           // maximum amount of time allotted to automl
  private final double _minAcc;          // minimum accuracy to achieve
  private final boolean _ensemble;       // allow ensembles?
  private final models[] _modelEx;       // model types to exclude; e.g. don't allow DL
  private final boolean _allowMutations; // allow for INPLACE mutations on input frame
  FrameMeta _fm;                         // metadata for _fr
  private boolean _isClassification;

  private long _timeRemaining;
  private long _totalTime;
  private transient ArrayList<Job> _jobs;

  public enum models { RF, GBM, GLM, GLRM, DL, KMEANS }  // consider EnumSet

  // https://0xdata.atlassian.net/browse/STEAM-52  --more interesting user options
  public AutoML(Key<AutoML> key, String datasetName, Frame fr, int response, String loss, long maxTime,
                double minAccuracy, boolean ensemble, models[] modelExclude, boolean tryMutations) {
    super(key);
    _datasetName=datasetName;
    _fr=fr;
    _response=response;
    _loss=loss;
    _maxTime=maxTime*1000;   // change to millis
    _minAcc=minAccuracy;
    _ensemble=ensemble;
    if( modelExclude!=null ) {
      HashSet<models> m = new HashSet<>();
      Collections.addAll(m,modelExclude);
      _modelEx = m.toArray(new models[m.size()]);
    } else _modelEx=null;
    _allowMutations=tryMutations;
    _jobs = new ArrayList<>();
  }

  public AutoML(Key<AutoML> key, String datasetName, Frame fr, String responseName, String loss, long maxTime,
                double minAccuracy, boolean ensemble, models[] modelExclude, boolean tryMutations ) {
    this(key,datasetName,fr,fr.find(responseName),loss,maxTime,minAccuracy,ensemble,modelExclude,tryMutations);
  }

  public static AutoML makeAutoML(Key<AutoML> key, String datasetPath, String responseName, String loss, long maxTime,
                double minAccuracy, boolean ensemble, models[] modelExclude, boolean tryMutations ) {
    ArrayList<String> files = new ArrayList();
    ArrayList<String> keys = new ArrayList();
    ArrayList<String> fails = new ArrayList();
    ArrayList<String> dels = new ArrayList();
    H2O.getPM().importFiles(datasetPath,files,keys,fails,dels);
    String datasetName = datasetPath.split("\\.(?=[^\\.]+$)")[0];
    Frame fr = ParseDataset.parse(Key.make(datasetName), Key.make(keys.get(0)));
    return new AutoML(key,datasetName,fr,fr.find(responseName),loss,maxTime,minAccuracy,ensemble,modelExclude,tryMutations);
  }

  // used to launch the AutoML asynchronously
  @Override public void run() {
    _totalTime = System.currentTimeMillis() + _maxTime;
    try {
      learn();
    } catch(AutoMLDoneException e) {
      // pass :)
    }
  }
  @Override public void stop() {
    if( null==_jobs ) return; // already stopped
    for(Job j: _jobs) j.stop();
    _totalTime=-1;
    _timeRemaining=-1;
    _jobs=null;
  }
  @Override public boolean keepRunning() { return (_timeRemaining = _totalTime - System.currentTimeMillis()) > 0; }
  @Override public long timeRemaining() { return _timeRemaining; }

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
    _fm = new FrameMeta(_fr, _response, _datasetName).computeFrameMetaPass1();
    _isClassification = _fm.isClassification();

    // step 2: build a fast RF
    ModelBuilder initModel = selectInitial(_fm);
    Model m = build(initModel); // need to track this...
    m._parms._train.remove();
    m._parms._valid.remove();
    System.out.println("AUTOML DONE");
    // gather more data? build more models? start applying transforms? what next ...?
    stop();
  }

  public Key<Model> getLeaderKey() {
    final Value val = DKV.get(LEADER);
    if( val==null ) return null;
    ModelLeader ml = val.get();
    return ml._leader;
  }
  public void delete() {
    _fr.delete();
    for(Model m: models()) m.delete();
    DKV.remove(MODELLIST);
    DKV.remove(LEADER);
  }

  private ModelBuilder selectInitial(FrameMeta fm) {  // may use _isClassification so not static method
    ModelBuilder mb = InitModel.initRF(fm._fr, fm.response()._name);
    mb._parms._ignored_columns = fm.ignoredCols();
    return mb;
  }

  // track models built by automl
  public final Key<Model> MODELLIST = Key.make("AutoMLModelList"+Key.make().toString(), (byte) 0, (byte) 2 /*builtin key*/, false);  // public for the test
  class ModelList extends Keyed {
    Key<Model>[] _models;
    ModelList() { super(MODELLIST); _models = new Key[0]; }
    @Override protected long checksum_impl() { throw H2O.fail("no such method for ModelList"); }
  }

  Model[] models() {
    final Value val = DKV.get(MODELLIST);
    if( val==null ) return new Model[0];
    ModelList ml = val.get();
    Model[] models = new Model[ml._models.length];
    int j=0;
    for( int i=0; i<ml._models.length; i++ ) {
      final Value model = DKV.get(ml._models[i]);
      if( model != null ) models[j++] = model.get();
    }
    assert j==models.length; // All models still exist
    return models;
  }

  public final Key<Model> LEADER = Key.make("AutoMLModelLeader"+Key.make().toString(), (byte) 0, (byte) 2, false);
  class ModelLeader extends Keyed {
    Key<Model> _leader;
    ModelLeader() { super(LEADER); _leader = null; }
    @Override protected long checksum_impl() { throw H2O.fail("no such method for ModelLeader"); }
  }

  Model leader() {
    final Value val = DKV.get(LEADER);
    if( val==null ) return null;
    ModelLeader ml = val.get();
    final Value leaderModelKey = DKV.get(ml._leader);
    assert null!=leaderModelKey; // if the LEADER is in the DKV, then there better be a model!
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
    if (leader == null || leaderKey.equals(leader._key) ) {
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
  // expected to only ever have a single AutoML instance going at a time
  Model build(ModelBuilder mb) {
    Job j;
    if( null==_jobs ) throw new AutoMLDoneException();
    _jobs.add(j=mb.trainModel());
    Model m = (Model)j.get();
    if( null==_jobs ) throw new AutoMLDoneException();
    _jobs.remove(j);
    final Key<Model> modelKey  = m._key;
    new TAtomic<ModelList>() {
      @Override public ModelList atomic(ModelList old) {
        if( old == null ) old = new ModelList();
        Key<Model>[] models = old._models;
        old._models = Arrays.copyOf(models, models.length + 1);
        old._models[models.length] = modelKey;
        return old;
      }
    }.invoke(MODELLIST);

    updateLeader(m);
    return m;
  }


  // satisfy typing for job return type...
  public static class AutoMLKeyV3 extends KeyV3<Iced, AutoMLKeyV3, AutoML>{
    public AutoMLKeyV3(){}
    public AutoMLKeyV3(Key<AutoML> key) { super(key); }
  }
  @Override public Class<AutoMLKeyV3> makeSchema() { return AutoMLKeyV3.class; }

  private class AutoMLDoneException extends H2OAbstractRuntimeException {
    public AutoMLDoneException() { this("done","done"); }
    public AutoMLDoneException(String msg, String dev_msg) {
      super(msg, dev_msg, new IcedHashMap.IcedHashMapStringObject());
    }
  }
}
