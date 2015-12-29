package ai.h2o.automl;

import ai.h2o.automl.strategies.initial.InitModel;
import hex.Model;
import hex.ModelBuilder;
import water.*;
import water.fvec.Frame;

import java.util.Arrays;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing multiple threads
 * of execution in an effort to discover an optimal supervised model for some given
 * (dataset, response, loss) combo.
 */
public final class AutoML {
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

  enum models { RF, GBM, GLM, GLRM, DL, KMEANS }  // consider EnumSet

  // https://0xdata.atlassian.net/browse/STEAM-52  --more interesting user options
  public AutoML(Frame fr, int response, String loss, long maxTime, double minAccuracy,
                boolean ensemble, String[] modelExclude, boolean allowMutations) {
    _fr=fr;
    _response=response;
    _loss=loss;
    _maxTime=maxTime;
    _minAcc=minAccuracy;
    _ensemble=ensemble;
    _modelEx=modelExclude==null?null:new models[modelExclude.length];
    if( modelExclude!=null )
      for( int i=0; i<modelExclude.length; ++i )
        _modelEx[i] = models.valueOf(modelExclude[i]);
    _allowMutations=allowMutations;
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
    _fm = new FrameMeta(_fr, _response).computeFrameMetaPass1();
    _isClassification = _fm.isClassification();

    // step 2: build a fast RF
    ModelBuilder initModel = selectInitial(_fm);
    initModel._parms._ignored_columns = _fm.ignoredCols();

    Model m = build(initModel); // need to track this...
    System.out.println("bink");
    // gather more data? build more models? start applying transforms? what next ...?
  }

  public void delete() {
    for(Model m: models()) m.delete();
    DKV.remove(MODELLIST);
    DKV.remove(LEADER);
  }

  private ModelBuilder selectInitial(FrameMeta fm) {  // may use _isClassification so not static method
    return InitModel.initRF(fm._fr, fm.response()._name);
  }


  // track models built by automl
  public static final Key<Model> MODELLIST = Key.make(" AutoMLModelList ", (byte) 0, (byte) 2 /*builtin key*/, false);  // public for the test
  static class ModelList extends Keyed {
    Key<Model>[] _models;
    ModelList() { super(MODELLIST); _models = new Key[0]; }
    @Override protected long checksum_impl() { throw H2O.fail("no such method for ModelList"); }
  }

  static Model[] models() {
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

  public static final Key<Model> LEADER = Key.make(" AutoMLModelLeader ", (byte) 0, (byte) 2, false);
  static class ModelLeader extends Keyed {
    Key _leader;
    ModelLeader() { super(LEADER); _leader = null; }
    @Override protected long checksum_impl() { throw H2O.fail("no such method for ModelLeader"); }
  }

  static Model leader() {
    final Value val = DKV.get(LEADER);
    if( val==null ) return null;
    ModelLeader ml = val.get();
    final Value model = DKV.get(ml._leader);
    assert model!=null; // if the LEADER is in the DKV, then there better be a model!
    return model.get();
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
    Model m = (Model)mb.trainModel().get();
    final Key modelKey  = m._key;
    new TAtomic<ModelList>() {
      @Override public ModelList atomic(ModelList old) {
        if( old == null ) old = new ModelList();
        Key[] models = old._models;
        old._models = Arrays.copyOf(models, models.length + 1);
        old._models[models.length] = modelKey;
        return old;
      }
    }.invoke(MODELLIST);

    updateLeader(m);
    return m;
  }
}
