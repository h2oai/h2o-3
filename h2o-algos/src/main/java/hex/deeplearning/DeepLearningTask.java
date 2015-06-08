package hex.deeplearning;

import hex.DataInfo;
import hex.FrameTask;
import water.DKV;
import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Key;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

public class DeepLearningTask extends FrameTask<DeepLearningTask> {
  final private boolean _training;

  private hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _localmodel; //per-node state (to be reduced)
  private hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _sharedmodel; //input/output

  // OUTPUT
  final public hex.deeplearning.DeepLearningModel.DeepLearningModelInfo model_info() {
    assert(_sharedmodel != null);
    return _sharedmodel;
  }

  transient Neurons[] _neurons;
  transient Random _dropout_rng;

  int _chunk_node_count = 1;

  public DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo inputModel, float fraction){this(jobKey, inputModel,fraction,null);}
  private DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo inputModel, float fraction, H2OCountedCompleter cmp){
    super(jobKey, inputModel.data_info(),cmp);
    assert(inputModel.get_processed_local() == 0);
    _training=true;
    _sharedmodel = inputModel;
    if (model_info().get_params()._elastic_averaging)
      DKV.put(_sharedmodel.sharedModelInfoKey(), _sharedmodel);
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
    _localmodel = null;
  }

  // transfer ownership from input to output (which will be worked on)
  @Override protected void setupLocal(){
    super.setupLocal();
    if (model_info().get_params()._elastic_averaging) {
      //Load my local model from DKV, to continue training
      _localmodel = DKV.getGet(_sharedmodel.localModelInfoKey(H2O.SELF));
      if (_localmodel != null) {
        // FIXME: remove
        {
          _localmodel.computeStats();
          assert (!_localmodel.unstable());
        }
        //Make sure that the local model has the right global (shared) parameters after checkpoint restart!
        _localmodel.set_params(_sharedmodel.get_params());
        _localmodel.set_processed_global(_sharedmodel.get_processed_global()); //TODO: CHECK
      }
      else {
        _localmodel = _sharedmodel.deep_clone();
      }
    } else {
      _localmodel = _sharedmodel;
    }
    _localmodel.set_processed_local(0); //TODO: CHECK
    // FIXME: remove
    {
      _localmodel.computeStats();
      assert (!_localmodel.unstable());
    }
  }

  // create local workspace (neurons)
  // and link them to shared weights
  @Override protected void chunkInit(){
    _neurons = makeNeuronsForTraining(_localmodel);
    _dropout_rng = RandomUtils.getRNG(System.currentTimeMillis());
  }

  @Override public final void processRow(long seed, DataInfo.Row r){
    assert !r.isSparse():"Deep learning does not support sparse rows.";
    if (model_info().get_params()._reproducible) {
      seed += model_info().get_processed_global(); //avoid periodicity
    } else {
      seed = _dropout_rng.nextLong(); // non-reproducible case - make a fast & good random number
    }
    ((Neurons.Input)_neurons[0]).setInput(seed, r.numVals, r.nBins, r.binIds);
    step(seed, _neurons, _localmodel, _sharedmodel, _training, r.response);
  }

  @Override protected void chunkDone(long n) {
    if (_training) _localmodel.add_processed_local(n);
  }

  // average the per-node models (already wrote them to DKV in postLocal())
  @Override public void reduce(DeepLearningTask other){
    if (_localmodel != null && other._localmodel != null && other._localmodel.get_processed_local() > 0 //other NNTask was active (its model_info should be used for averaging)
            && other._localmodel != _localmodel) //other NNTask worked on a different model_info
    {
      // avoid adding remote model info to unprocessed local data, still random
      // (this can happen if we have no chunks on the master node)
      if (_localmodel.get_processed_local() == 0) {
        _localmodel = other._localmodel;
        _chunk_node_count = other._chunk_node_count;
      } else {
        _localmodel.add(other._localmodel);
        _chunk_node_count += other._chunk_node_count;
      }
      if (other._localmodel.unstable()) _localmodel.set_unstable();
      // FIXME: remove
      {
        _localmodel.computeStats();
        assert (!_localmodel.unstable());
      }
    }
  }

  @Override
  protected void postLocal() {
    if (model_info().get_params()._elastic_averaging) {
      // FIXME: remove
      {
        _localmodel.computeStats();
        assert (!_localmodel.unstable());
      }
      // store local model, as it will be reduced in the following, and hence corrupted
      DKV.put(_localmodel.localModelInfoKey(H2O.SELF), _localmodel); //don't store under _localmodel._key - can be the same as _sharedmodel._key...
//      _sharedmodel = null; //don't want to accidentally ship & reduce the consensus model
    }
    super.postLocal();
  }

  static long _lastWarn;
  static long _warnCount;
  @Override protected void postGlobal(){
    DeepLearningModel.DeepLearningParameters dlp = _localmodel.get_params();
    if (H2O.CLOUD.size() > 1 && !dlp._replicate_training_data) {
      long now = System.currentTimeMillis();
      if (_chunk_node_count < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
//        Log.info("Synchronizing across " + _chunk_node_count + " H2O node(s).");
        Log.warn(H2O.CLOUD.size() - _chunk_node_count + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    if (!dlp._replicate_training_data || H2O.CLOUD.size() == 1) {
      _localmodel.add_processed_global(_localmodel.get_processed_local()); //move local sample counts to global ones
      _localmodel.set_processed_local(0l);
      if (_chunk_node_count > 1) _localmodel.div(_chunk_node_count);

      if (dlp._elastic_averaging) {
        // FIXME: remove
        {
          _localmodel.computeStats();
          assert (!_localmodel.unstable());
        }
        // Cf. equation 6 of arXiv:1412.6651v5
        final float pa = (float)_localmodel.get_params()._elastic_averaging_moving_rate;

        // _localmodel : current average of per-node models
        // _sharedmodel: time-average of node-averages (consensus model, "the" model)

        _sharedmodel = DKV.getGet(_sharedmodel.sharedModelInfoKey()); //get latest version from DKV
        _localmodel.mult(pa);
        _sharedmodel.mult(1 - pa);
        _sharedmodel.add(_localmodel); //ignore processed local value set here
        _sharedmodel.set_processed_global(_localmodel.get_processed_global());
        _sharedmodel.set_processed_local(0);

        // FIXME: remove
        {
          _sharedmodel.computeStats();
          assert (!_sharedmodel.unstable());
        }

        DKV.put(_sharedmodel.sharedModelInfoKey(), _sharedmodel);
//      DKV.put(_sharedmodel.data_info()); //FIXME: Needed for Elastic Averaging?
        _localmodel = null;
      }
      else {
        _sharedmodel = _localmodel;
      }
    }
  }

  public static Neurons[] makeNeuronsForTraining(final DeepLearningModel.DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, true);
  }
  public static Neurons[] makeNeuronsForTesting(final DeepLearningModel.DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, false);
  }

  // Helper
  private static Neurons[] makeNeurons(final DeepLearningModel.DeepLearningModelInfo minfo, boolean training) {
    DataInfo dinfo = minfo.data_info();
    final DeepLearningModel.DeepLearningParameters params = minfo.get_params();
    final int[] h = params._hidden;
    Neurons[] neurons = new Neurons[h.length + 2]; // input + hidden + output
    // input
    neurons[0] = new Neurons.Input(minfo.units[0], dinfo);
    // hidden
    for( int i = 0; i < h.length + (params._autoencoder ? 1 : 0); i++ ) {
      int n = params._autoencoder && i == h.length ? minfo.units[0] : h[i];
      switch( params._activation ) {
        case Tanh:
          neurons[i+1] = new Neurons.Tanh(n);
          break;
        case TanhWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Tanh(n) : new Neurons.TanhDropout(n);
          break;
        case Rectifier:
          neurons[i+1] = new Neurons.Rectifier(n);
          break;
        case RectifierWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Rectifier(n) : new Neurons.RectifierDropout(n);
          break;
        case Maxout:
          neurons[i+1] = new Neurons.Maxout(n);
          break;
        case MaxoutWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Maxout(n) : new Neurons.MaxoutDropout(n);
          break;
      }
    }
    if(!params._autoencoder) {
      if (minfo._classification)
        neurons[neurons.length - 1] = new Neurons.Softmax(minfo.units[minfo.units.length - 1]);
      else
        neurons[neurons.length - 1] = new Neurons.Linear(1);
    }

    //copy parameters from NN, and set previous/input layer links
    for( int i = 0; i < neurons.length; i++ ) {
      neurons[i].init(neurons, i, params, minfo, training);
      neurons[i]._input = neurons[0];
    }

//    // debugging
//    for (Neurons n : neurons) Log.info(n.toString());
    return neurons;
  }

  // forward/backward propagation
  // assumption: layer 0 has _a filled with (horizontalized categoricals) double values
  public static void step(long seed, Neurons[] neurons, DeepLearningModel.DeepLearningModelInfo minfo,
                          DeepLearningModel.DeepLearningModelInfo consensus_minfo, boolean training, double[] responses) {
    try {
      for (int i=1; i<neurons.length-1; ++i) {
        neurons[i].fprop(seed, training);
      }
      if (minfo.get_params()._autoencoder) {
        neurons[neurons.length - 1].fprop(seed, training);
        if (training) {
          for (int i=neurons.length-1; i>0; --i) {
            neurons[i].bprop();
          }
        }
      } else {
        if (consensus_minfo != null) {
          for (int i = 1; i < neurons.length; i++) {
            neurons[i]._wEA = consensus_minfo.get_weights(i - 1);
            neurons[i]._bEA = consensus_minfo.get_biases(i - 1);
          }
        }
        if (minfo._classification) {
          ((Neurons.Softmax) neurons[neurons.length - 1]).fprop();
          if (training) {
            for (int i = 1; i < neurons.length - 1; i++)
              Arrays.fill(neurons[i]._e.raw(), 0);
            assert ((double) (int) responses[0] == responses[0]);
            final int target_label;
            if (Double.isNaN(responses[0])) { //missing response
              target_label = Neurons.missing_int_value;
            } else {
              assert ((double) (int) responses[0] == responses[0]); //classification -> integer labels expected
              target_label = (int) responses[0];
            }
            ((Neurons.Softmax) neurons[neurons.length - 1]).bprop(target_label);
          }
        } else {
          ((Neurons.Linear) neurons[neurons.length - 1]).fprop();
          if (training) {
            for (int i = 1; i < neurons.length - 1; i++)
              Arrays.fill(neurons[i]._e.raw(), 0);
            float target_value;
            if (Double.isNaN(responses[0])) { //missing response
              target_value = Neurons.missing_real_value;
            } else {
              target_value = (float) responses[0];
            }
            ((Neurons.Linear) neurons[neurons.length - 1]).bprop(target_value);
          }
        }
        if (training) {
          for (int i = neurons.length - 2; i > 0; --i)
            neurons[i].bprop();
        }
      }
    }
    catch(Throwable ex) {
      Log.warn(ex.getMessage());
      minfo.set_unstable();
      throw ex;
    }
  }

}
