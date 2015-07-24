package hex.deeplearning;

import hex.DataInfo;
import hex.Distribution;
import hex.FrameTask;
import water.DKV;
import water.H2O;
import water.Key;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

public class DeepLearningTask extends FrameTask<DeepLearningTask> {
  final private boolean _training;
  private DeepLearningModelInfo _localmodel; //per-node state (to be reduced)
  private DeepLearningModelInfo _sharedmodel; //input/output
  transient Neurons[] _neurons;
  transient Random _dropout_rng;
  int _chunk_node_count = 1;

  /**
   * Accessor to the object containing the (final) state of the Deep Learning model
   * Should only be queried after calling this.doAll(Frame training)
   * @return "The" final model after one Map/Reduce iteration
   */
  final public DeepLearningModelInfo model_info() {
    assert(_sharedmodel != null);
    return _sharedmodel;
  }

  /**
   * The only constructor
   * @param jobKey
   * @param inputModel Initial model state
   * @param fraction Fraction of rows of the training to train with
   * @param iteration
   */
  public DeepLearningTask(Key jobKey, DeepLearningModelInfo inputModel, float fraction, int iteration){
    super(jobKey, inputModel.data_info(),inputModel.get_params()._seed + inputModel.get_processed_global(), iteration);
    assert(inputModel.get_processed_local() == 0);
    _training=true;
    _sharedmodel = inputModel;
//    if (model_info().get_params()._elastic_averaging)
//      DKV.put(_sharedmodel.elasticAverageModelInfoKey(), _sharedmodel);
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
  }

  /**
   * Transfer ownership from global (shared) model to local model which will be worked on
   */
  @Override protected void setupLocal(){
    assert(_localmodel == null);
    super.setupLocal();
    if (model_info().get_params()._elastic_averaging) {
      //Load my local model from DKV, to continue training
      _localmodel = DKV.getGet(_sharedmodel.localModelInfoKey(H2O.SELF));
      if (_localmodel != null) {
        if (!Arrays.equals(_localmodel.units, _sharedmodel.units)) {
          _localmodel = _sharedmodel.deep_clone();
        } else {
          //Make sure that the local model has the right global (shared) parameters after checkpoint restart!
          _localmodel.set_params(_sharedmodel.get_params());
          _localmodel.set_processed_global(_sharedmodel.get_processed_global());
        }
      }
      else {
        // first time around - use the randomized initial weights and don't spread the shared (random) model
        _localmodel = _sharedmodel.deep_clone();
        _sharedmodel = null;
      }
    } else {
      _localmodel = _sharedmodel;
      _sharedmodel = null;
    }
    _localmodel.set_processed_local(0);
  }

  // Create local workspace (neurons) and link them to shared weights
  @Override protected boolean chunkInit(){
    if (_localmodel.get_processed_local() >= _useFraction * _fr.numRows())
      return false;
    _neurons = makeNeuronsForTraining(_localmodel);
    _dropout_rng = RandomUtils.getRNG(System.currentTimeMillis());
    return true;
  }

  /**
   * Process one training row at a time (online learning)
   * @param seed Seed is only used if reproducible mode is enabled
   * @param r Row (must be dense for now)
   */
  @Override public final void processRow(long seed, DataInfo.Row r){
    assert !r.isSparse():"Deep learning does not support sparse rows.";
    if (_localmodel.get_params()._reproducible) {
      seed += _localmodel.get_processed_global(); //avoid periodicity
    } else {
      seed = _dropout_rng.nextLong(); // non-reproducible case - make a fast & good random number
    }
    _localmodel.checkMissingCats(r.binIds);
    ((Neurons.Input)_neurons[0]).setInput(seed, r.numVals, r.nBins, r.binIds);
    step(seed, _neurons, _localmodel, _localmodel.get_params()._elastic_averaging ? _sharedmodel : null, _training, r.response, r.offset);
  }

  /**
   * After each chunk, add the number of processed rows to the counter
   * @param n Number of processed rows
   */
  @Override protected void chunkDone(long n) {
    if (_training) _localmodel.add_processed_local(n);
  }

  /**
   * After all maps are done on a node, this is called to store the per-node model into DKV (for elastic averaging)
   * Otherwise, do nothing.
   */
  @Override protected void postLocal() {
    if (_localmodel.get_params()._elastic_averaging) {
      // store local model, as it will be reduced in the following, and hence averaged with other models
      DKV.put(_localmodel.localModelInfoKey(H2O.SELF), _localmodel);
    }
    _sharedmodel = null; //avoid serialization overhead
    super.postLocal();
  }

  /**
   * Average the per-node models (for elastic averaging, already wrote them to DKV in postLocal())
   * This is a no-op between F/J worker threads (operate on the same weights/biases)
   * @param other
   */
  @Override public void reduce(DeepLearningTask other){
    if (_localmodel != null && other._localmodel != null && other._localmodel.get_processed_local() > 0 //other DLTask was active (its model_info should be used for averaging)
            && other._localmodel != _localmodel) //other DLTask worked on a different model_info
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
  /**
   * After all reduces are done, the driver node calls this method to clean up
   * This is only needed if we're not inside a DeepLearningTask2 (which will do the reduction between replicated data workers).
   * So if replication is disabled, and every node works on partial data, then we have work to do here (model averaging).
   */
  @Override protected void postGlobal(){
    DeepLearningParameters dlp = _localmodel.get_params();
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
    // Check that we're not inside a DeepLearningTask2
    assert ((!dlp._replicate_training_data || H2O.CLOUD.size() == 1) == !_run_local);
    if (!_run_local) {
      _localmodel.add_processed_global(_localmodel.get_processed_local()); //move local sample counts to global ones
      _localmodel.set_processed_local(0l);
      // model averaging
      if (_chunk_node_count > 1)
        _localmodel.div(_chunk_node_count);
      if (_localmodel.get_params()._elastic_averaging)
        _sharedmodel = DeepLearningModelInfo.timeAverage(_localmodel);
    } else {
      //Get ready for reduction in DeepLearningTask2
      //Just swap the local and global models
      _sharedmodel = _localmodel;
    }
    if (_sharedmodel == null)
      _sharedmodel = _localmodel;
    _localmodel = null;
  }

  public static Neurons[] makeNeuronsForTraining(final DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, true);
  }
  public static Neurons[] makeNeuronsForTesting(final DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, false);
  }

  // Helper
  private static Neurons[] makeNeurons(final DeepLearningModelInfo minfo, boolean training) {
    DataInfo dinfo = minfo.data_info();
    final DeepLearningParameters params = minfo.get_params();
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

  /**
   * Forward/Backward propagation
   * assumption: layer 0 has _a filled with (horizontalized categoricals) double values
   * @param seed
   * @param neurons
   * @param minfo
   * @param consensus_minfo
   * @param training
   * @param responses
   */
  public static void step(long seed, Neurons[] neurons, DeepLearningModelInfo minfo,
                          DeepLearningModelInfo consensus_minfo, boolean training, double[] responses, double offset) {
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
          // compute prediction (in link space)
          ((Neurons.Linear) neurons[neurons.length - 1]).fprop();
          // add offset (in link space)
          if (offset > 0) {
            double mul = minfo.data_info()._normRespMul == null ? 1 : minfo.data_info()._normRespMul[0];
            double sub = minfo.data_info()._normRespSub == null ? 0 : minfo.data_info()._normRespSub[0];
            neurons[neurons.length - 1]._a.add(0, (float) ((offset - sub) * mul));
          }
//          //bring prediction to response space
//          float pred = neurons[neurons.length - 1]._a.get(0);
//          pred = (float)new Distribution(minfo.get_params()._distribution, minfo.get_params()._tweedie_power).link(pred); //bring (descaled) response back to link domain
//          neurons[neurons.length - 1]._a.set(0, pred);

          if (training) {
            for (int i = 1; i < neurons.length - 1; i++)
              Arrays.fill(neurons[i]._e.raw(), 0);
            float target_value;
            if (Double.isNaN(responses[0])) { //missing response
              target_value = Neurons.missing_real_value;
            } else {
              target_value = (float)responses[0]; //actual response in response space
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
