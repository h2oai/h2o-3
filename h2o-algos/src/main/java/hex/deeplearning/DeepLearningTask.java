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
  private hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _sharedmodel; //"consensus" model
  hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _mymodel; //my workspace
  final public hex.deeplearning.DeepLearningModel.DeepLearningModelInfo model_info() { return _mymodel; }

  transient Neurons[] _neurons;
  transient Random _dropout_rng;

  int _chunk_node_count = 1;
  boolean consensusADMM = true;

  public DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo sharedModel, float fraction){this(jobKey, sharedModel,fraction,null);}
  private DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo sharedModel, float fraction, H2OCountedCompleter cmp){
    super(jobKey, sharedModel.data_info(),cmp);
    _training=true;
    _sharedmodel = sharedModel;
    _useFraction=fraction;
    _shuffle = _sharedmodel.get_params()._shuffle_training_data;
    assert(_mymodel == null);
  }

  // transfer ownership from input to output (which will be worked on)
  @Override protected void setupLocal(){
    super.setupLocal();
    if (consensusADMM) {
//      Log.info("Loading my local model checkpoint.");
      if (DKV.get(_sharedmodel.myModelInfoKey(H2O.SELF)) != null) {
        _mymodel = DKV.getGet(_sharedmodel.myModelInfoKey(H2O.SELF));
      } else {
//        Log.info("Starting with global initial model.");
        _mymodel = _sharedmodel; //first time
      }
    } else {
      _mymodel = _sharedmodel; //faster, good enough in this case (since the input was freshly deserialized by the Weaver)
      _sharedmodel = null;
    }
//    _mymodel.set_processed_local(0l);
  }

  // create local workspace (neurons)
  // and link them to shared weights
  @Override protected void chunkInit(){
    _neurons = makeNeuronsForTraining(_mymodel);
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
    step(seed, _neurons, _mymodel, _sharedmodel, _training, r.response);
  }

  @Override protected void chunkDone(long n) {
    if (_training) {
//      Log.info("Chunk done. Updating local model.");
      _mymodel.add_processed_local(n);
    }
  }

  @Override
  protected void postLocal() {
    if (consensusADMM) {
//      Log.info("Storing my local model checkpoint.");
      DKV.put(_sharedmodel.myModelInfoKey(H2O.SELF), _mymodel);
//      Log.info("Getting my local model ready for reduction.");
//      assert(_output == null);
      _sharedmodel = _mymodel.deep_clone(); //no longer need _sharedmodel for training, use it send back
    } else {
      _sharedmodel = _mymodel;
    }
    super.postLocal();
  }


  @Override public void reduce(DeepLearningTask other){
    if (_sharedmodel != null && other._sharedmodel != null && other._sharedmodel.get_processed_local() > 0 //other NNTask was active (its model_info should be used for averaging)
            && other._sharedmodel != _sharedmodel) //other NNTask worked on a different model_info
    {
      // avoid adding remote model info to unprocessed local data, still random
      // (this can happen if we have no chunks on the master node)
      if (_sharedmodel.get_processed_local() == 0) {
        _sharedmodel = other._sharedmodel;
        _chunk_node_count = other._chunk_node_count;
      } else {
        _sharedmodel.add(other._sharedmodel);
        _chunk_node_count += other._chunk_node_count;
      }
      if (other._sharedmodel.unstable()) _sharedmodel.set_unstable();
    }
  }

  static long _lastWarn;
  static long _warnCount;
  @Override protected void postGlobal(){
    if (H2O.CLOUD.size() > 1 && !_mymodel.get_params()._replicate_training_data) {
      long now = System.currentTimeMillis();
      if (_chunk_node_count < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
//        Log.info("Synchronizing across " + _chunk_node_count + " H2O node(s).");
        Log.warn(H2O.CLOUD.size() - _chunk_node_count + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    if (_sharedmodel!=null && (!_sharedmodel.get_params()._replicate_training_data || H2O.CLOUD.size() == 1) ) {
      _sharedmodel.div(_chunk_node_count);
      _sharedmodel.add_processed_global(_sharedmodel.get_processed_local());
      _sharedmodel.set_processed_local(0l);
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
            neurons[i]._wConsensus = consensus_minfo.get_weights(i - 1);
            neurons[i]._bConsensus = consensus_minfo.get_biases(i - 1);
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
    catch(RuntimeException ex) {
      Log.warn(ex.getMessage());
      minfo.set_unstable();
      throw new RuntimeException("Canceling job due to numerical instability.");
    }
  }

}
