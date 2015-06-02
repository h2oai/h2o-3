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

  //for consensusADMM only: per-node model, never sent to anyone else
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
  boolean consensusADMM = false;

  public DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo inputModel, float fraction){this(jobKey, inputModel,fraction,null);}
  private DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo inputModel, float fraction, H2OCountedCompleter cmp){
    super(jobKey, inputModel.data_info(),cmp);
    assert(inputModel.get_processed_local() == 0);
    _training=true;
    _sharedmodel = inputModel;
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
  }

  // transfer ownership from input to output (which will be worked on)
  @Override protected void setupLocal(){
    super.setupLocal();
    if (consensusADMM) {
      //Load my local model from DKV, to continue training
      _localmodel = DKV.getGet(_sharedmodel.localModelInfoKey(H2O.SELF));
      if (_localmodel != null) {
        //Make sure that the local model has the right global (shared) parameters after checkpoint restart!
        _localmodel.set_params(_sharedmodel.get_params());

        //Set the number of trained samples according to global model
        _localmodel.set_processed_global(_sharedmodel.get_processed_global()); //TODO
        _localmodel.set_processed_local(0); //TODO
      }
    }
    // The first time around (or no ADMM): There's no local model yet, just use the input model
    if (_localmodel == null) _localmodel = _sharedmodel;
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

  @Override
  protected void postLocal() {
    if (consensusADMM) {
      // store local model, as it will be reduced in the following, and hence corrupted
      DKV.put(_localmodel.localModelInfoKey(H2O.SELF), _localmodel);
      _sharedmodel = null; //don't want to accidentally ship & reduce the consensus model
    }
    super.postLocal();
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
    }
  }

  static long _lastWarn;
  static long _warnCount;
  @Override protected void postGlobal(){
    if (H2O.CLOUD.size() > 1 && !_localmodel.get_params()._replicate_training_data) {
      long now = System.currentTimeMillis();
      if (_chunk_node_count < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
//        Log.info("Synchronizing across " + _chunk_node_count + " H2O node(s).");
        Log.warn(H2O.CLOUD.size() - _chunk_node_count + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    if (!_localmodel.get_params()._replicate_training_data && H2O.CLOUD.size() == 1) {
      _localmodel.add_processed_global(_localmodel.get_processed_local()); //move local sample counts to global ones
      _localmodel.set_processed_local(0l);
      if (_chunk_node_count > 1) _localmodel.div(_chunk_node_count);
    }

    if (consensusADMM) {
//        assert(_localmodel.get_processed_global() > _sharedmodel.get_processed_global());
//        _sharedmodel.div(1.f/0.9f); //multiply by 0.9 - time average
//        _localmodel.div(10f); //add 1/10 of the averaged per-node models
//        _sharedmodel.add(_localmodel);
//        _localmodel = null; //each node needs to pull its local model again from DKV
      _sharedmodel = (DeepLearningModel.DeepLearningModelInfo)_localmodel.clone(); //FIXME: consensus is just the average model for now
      DKV.put(_sharedmodel.data_info());
    }
    else {
      _sharedmodel = _localmodel;
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
