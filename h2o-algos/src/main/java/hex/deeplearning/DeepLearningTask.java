package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.DataInfo;
import hex.FrameTask;
import water.DKV;
import water.H2O;
import water.IcedUtils;
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
    this(jobKey,inputModel,fraction,iteration,null);
//    {
//      throw new UnsupportedOperationException("I don't want tasks");
//    }
  }
  public DeepLearningTask(Key jobKey, DeepLearningModelInfo inputModel, float fraction, int iteration, H2O.H2OCountedCompleter cmp){
    super(jobKey, inputModel.data_info(),inputModel.get_params()._seed + inputModel.get_processed_global(), iteration, inputModel.get_params()._sparse,cmp);
    assert(inputModel.get_processed_local() == 0);
    _training=true;
    _sharedmodel = inputModel;
//    if (model_info().get_params()._elastic_averaging)
//      DKV.put(_sharedmodel.elasticAverageModelInfoKey(), _sharedmodel);
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
//    {
//      throw new UnsupportedOperationException("I don't want tasks");
//    }
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
          _localmodel = IcedUtils.deepCopy(_sharedmodel);
        } else {
          //Make sure that the local model has the right global (shared) parameters after checkpoint restart!
          _localmodel.set_params(_sharedmodel.get_params(), _sharedmodel._model_id);
          _localmodel.set_processed_global(_sharedmodel.get_processed_global());
        }
      }
      else {
        // first time around - use the randomized initial weights and don't spread the shared (random) model
        _localmodel = IcedUtils.deepCopy(_sharedmodel);
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
    _neurons = _localmodel.neuronsForTraining();
    _dropout_rng = RandomUtils.getRNG(System.currentTimeMillis());
    return true;
  }

  /**
   * Process one training row at a time (online learning)
   * @param seed Seed is only used if reproducible mode is enabled
   * @param r Row (must be dense for now)
   * @param mb mini-batch internal index
   */
  @Override public final void processRow(long seed, DataInfo.Row r, int mb) {
    if (_localmodel.get_params()._reproducible) {
      seed += _localmodel.get_processed_global(); //avoid periodicity
    } else {
      seed = _dropout_rng.nextLong(); // non-reproducible case - make a fast & good random number
    }
    _localmodel.checkMissingCats(r.binIds);
    ((Neurons.Input) _neurons[0]).setInput(seed, r.isSparse() ? r.numIds : null, r.numVals, r.nBins, r.binIds, mb);
  }

  /**
   * Apply the gradient to update the weights
   * @param seed
   * @param responses
   * @param offsets
   * @param n number of trained examples in this last mini batch (usually == mini_batch_size, but can be less)
   */
  @Override public void processMiniBatch(long seed, double[] responses, double[] offsets, int n) {
    assert(_training);
    if (_localmodel.get_params()._reproducible) {
      seed += _localmodel.get_processed_global(); //avoid periodicity
    } else {
      seed = _dropout_rng.nextLong(); // non-reproducible case - make a fast & good random number
    }
    Neurons.fpropMiniBatch(seed, _neurons, _localmodel, _localmodel.get_params()._elastic_averaging ? _sharedmodel : null, _training, responses, offsets, n);
    bpropMiniBatch(_neurons, n);
  }

  /**
   * Helper to apply back-propagation without clearing out the gradients afterwards
   * Used for gradient checking
   * @param neurons
   * @param n number of trained examples in this last mini batch (usually == mini_batch_size, but can be less)
   */
  static public void bpropMiniBatch(Neurons[] neurons, int n) {
    neurons[neurons.length - 1].bpropOutputLayer(n);
    for (int i = neurons.length - 2; i > 0; --i)
      neurons[i].bprop(n);

    for (int mb=0;mb<n;++mb) {
      // all errors are reset to 0
      for (int i = 0; i<neurons.length ;++i) {
        Storage.DenseVector e = neurons[i]._e == null ? null : neurons[i]._e[mb];
        if (e==null) continue;
        Arrays.fill(e.raw(), 0);
      }
    }
  }

  @Override
  protected int getMiniBatchSize() {
    return _localmodel.get_params()._mini_batch_size;
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
  @Override protected void closeLocal() {
    if (_localmodel.get_params()._elastic_averaging) {
      // store local model, as it will be reduced in the following, and hence averaged with other models
      DKV.put(_localmodel.localModelInfoKey(H2O.SELF), _localmodel, _fs);
    }
    _sharedmodel = null; //avoid serialization overhead
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
      if (other._localmodel.isUnstable()) _localmodel.setUnstable();
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

}
