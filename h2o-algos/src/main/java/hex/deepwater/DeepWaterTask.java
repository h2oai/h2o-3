package hex.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendTrain;
import hex.FrameTask;
import water.Futures;
import water.H2O;
import water.Job;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class DeepWaterTask extends FrameTask<DeepWaterTask> {
  private DeepWaterModelInfo _localmodel; //per-node state (to be reduced)
  private DeepWaterModelInfo _sharedmodel; //input/output
  private int _chunk_node_count = 1;
  private float _useFraction;
  private boolean _shuffle;
  private final Job _job;

  /**
   * Accessor to the object containing the (final) state of the Deep Learning model
   * Should only be queried after calling this.doAll(Frame training)
   * @return "The" final model after one Map/Reduce iteration
   */
  final public DeepWaterModelInfo model_info() {
    assert(_sharedmodel != null);
    return _sharedmodel;
  }

  /**
   * The only constructor
   * @param inputModel Initial model state
   * @param fraction Fraction of rows of the training to train with
   */
  DeepWaterTask(DeepWaterModelInfo inputModel, float fraction, Job job) {
    super(job._key,inputModel._dataInfo);
    _sharedmodel = inputModel;
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
    _job = job;
  }

  /**
   * Transfer ownership from global (shared) model to local model which will be worked on
   */
  @Override protected void setupLocal(){
//    long start = System.currentTimeMillis();
    assert(_localmodel == null);
    _localmodel = _sharedmodel;
    _sharedmodel = null;
    _localmodel.set_processed_local(0);
    final int weightIdx =_fr.find(_localmodel.get_params()._weights_column);
    final int respIdx =_fr.find(_localmodel.get_params()._response_column);
    final int batchSize = _localmodel.get_params()._mini_batch_size;

//    long nativetime = 0;
    DeepWaterIterator iter = null;
    long seed = 0xDECAF + 0xD00D * _localmodel.get_processed_global();
    Random rng = RandomUtils.getRNG(seed);

    if (_fr.numRows()>Integer.MAX_VALUE) {
      throw H2O.unimpl("Need to implement batching into int-sized chunks.");
    }
    int len = (int)_fr.numRows();
    int j=0;
    Futures fs = new Futures();
    ArrayList trainLabels = new ArrayList<>();
    ArrayList trainData = new ArrayList<>();

    try {
      // Binary data (Images/Documents/etc.)
      if (_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.image ||
          _localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
        int dataIdx = 0; //must be the first column //FIXME
        Log.debug("Using column " + _fr.name(dataIdx) + " for " +
            ((_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.image) ? "path to image data"
                :((_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.text) ? "text data"
                : "path to arbitrary bytes")));
        // full passes over the data
        BufferedString bs = new BufferedString();
        int fullpasses = (int)_useFraction; // Example: train_samples_per_iteration = 4700, and train.numRows()=1000 -> _useFraction = 4.7 -> fullpasses = 4
        while (j++ < fullpasses) {
          for (int i=0; i<_fr.numRows(); ++i) {
            double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
            if (weight == 0)
              continue;
            BufferedString file = _fr.vec(dataIdx).atStr(bs, i);
            if (file!=null)
              trainData.add(file.toString());
            float response = (float) _fr.vec(respIdx).at(i);
            trainLabels.add(response);
          }
        }

        // fractional passes // 0.7
        while (trainData.size() < _useFraction*len || trainData.size() % batchSize != 0) {
          assert(_shuffle);
          int i = rng.nextInt(len);
          double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
          if (weight == 0)
            continue;
          BufferedString file = _fr.vec(dataIdx).atStr(bs, i);
          if (file!=null)
            trainData.add(file.toString());
          float response = (float) _fr.vec(respIdx).at(i);
          trainLabels.add(response);
        }
      }

      // Numeric data (H2O Frame full with numeric columns)
      else if (_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
        double mul = _localmodel._dataInfo._normRespMul!=null ? _localmodel._dataInfo._normRespMul[0] : 1;
        double sub = _localmodel._dataInfo._normRespSub!=null ? _localmodel._dataInfo._normRespSub[0] : 0;

        // full passes over the data
        int fullpasses = (int) _useFraction;
        while (j++ < fullpasses) {
          for (int i = 0; i < _fr.numRows(); ++i) {
            double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
            if (weight == 0)
              continue;
            float response = (float)((_fr.vec(respIdx).at(i) - sub) / mul);
            trainData.add(i);
            trainLabels.add(response);
          }
        }

        // fractional passes
        while (trainData.size() < _useFraction * len || trainData.size() % batchSize != 0) {
          int i = rng.nextInt(len);
          double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
          if (weight == 0)
            continue;
          float response = (float)((_fr.vec(respIdx).at(i) - sub) / mul);
          trainData.add(i);
          trainLabels.add(response);
        }
      }

      // shuffle the (global) list
      if (_shuffle) {
        rng.setSeed(seed);
        Collections.shuffle(trainLabels, rng);
        rng.setSeed(seed);
        Collections.shuffle(trainData, rng);
      }
      if (_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.image) {
        iter = new DeepWaterImageIterator(trainData, trainLabels, _localmodel._meanData, batchSize, _localmodel._width, _localmodel._height, _localmodel._channels, _localmodel.get_params()._cache_data);
      }
      else if (_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
        assert (_localmodel._dataInfo != null);
        iter = new DeepWaterDatasetIterator(trainData, trainLabels, _localmodel._dataInfo, batchSize, _localmodel.get_params()._cache_data);
      }
      else if (_localmodel.get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
        iter = new DeepWaterTextIterator(trainData, trainLabels, batchSize, 56/*FIXME*/, _localmodel.get_params()._cache_data);
      }

      NativeTrainTask ntt;
      while (iter.Next(fs) && !_job.isStopping()) {
//        if (ntt != null) nativetime += ntt._timeInMillis;
        long n = _localmodel.get_processed_total();

//        if(!_localmodel.get_params()._quiet_mode)
//          Log.info("Trained " + n + " samples. Training on " + Arrays.toString(((DeepWaterImageIterator)iter).getFiles()));

        _localmodel._backend.setParameter(_localmodel.getModel().get(), "learning_rate", _localmodel.get_params().learningRate((double) n));
        _localmodel._backend.setParameter(_localmodel.getModel().get(), "momentum", _localmodel.get_params().momentum((double) n));

        //fork off GPU work, but let the iterator.Next() wait on completion before swapping again
        //System.err.println("data: " + Arrays.toString(iter.getData()));
        /*
        float[] preds = _localmodel._backend.predict(_localmodel._model, iter.getData());
        if (Float.isNaN(ArrayUtils.sum(preds))) {
          Log.err(DeepWaterModel.unstable_msg);
          throw new UnsupportedOperationException(DeepWaterModel.unstable_msg);
        }
        */
//        System.err.println("pred: " + Arrays.toString(preds));
        ntt = new NativeTrainTask(_localmodel._backend, _localmodel.getModel().get(), iter.getData(), iter.getLabel());
        fs.add(H2O.submitTask(ntt));
        _localmodel.add_processed_local(iter._batch_size);
      }
      fs.blockForPending();
//      nativetime += ntt._timeInMillis;
    } catch (IOException e) {
      e.printStackTrace(); //gracefully continue if we can't find files etc.
    }
//    long end = System.currentTimeMillis();
//    if (!_localmodel.get_params()._quiet_mode) {
//      Log.info("Time for one iteration: " + PrettyPrint.msecs(end - start, true));
//      Log.info("Time for native training : " + PrettyPrint.msecs(nativetime, true));
//    }
  }
  @Override public void map(Chunk [] chunks, NewChunk [] outputs) { }

  static private class NativeTrainTask extends H2O.H2OCountedCompleter<NativeTrainTask> {

    long _timeInMillis;
    final BackendTrain _backend;
    final BackendModel _model;

    float[] _data;
    float[] _labels;

    NativeTrainTask(BackendTrain backend, BackendModel model, float[] data, float[] label) {
      _backend = backend;
      _model = model;
      _data = data;
      _labels = label;
    }

    @Override
    public void compute2() {
      long start = System.currentTimeMillis();
      _backend.train(_model, _data,_labels); //ignore predictions
      long end = System.currentTimeMillis();
      _timeInMillis += end-start;
      tryComplete();
    }
  }

  /**
   * After all maps are done on a node, this is called to store the per-node model into DKV (for elastic averaging)
   * Otherwise, do nothing.
   */
  @Override protected void closeLocal() {
    _sharedmodel = null;
  }

  /**
   * Average the per-node models (for elastic averaging, already wrote them to DKV in postLocal())
   * This is a no-op between F/J worker threads (operate on the same weights/biases)
   * @param other Other DeepWaterTask to reduce
   */
  @Override public void reduce(DeepWaterTask other){
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
    }
  }


  private static long _lastWarn;
  private static long _warnCount;
  /**
   * After all reduces are done, the driver node calls this method to clean up
   * This is only needed if we're not inside a DeepWaterTask2 (which will do the reduction between replicated data workers).
   * So if replication is disabled, and every node works on partial data, then we have work to do here (model averaging).
   */
  @Override protected void postGlobal(){
    DeepWaterParameters dlp = _localmodel.get_params();
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
    // Check that we're not inside a DeepWaterTask2
    assert ((!dlp._replicate_training_data || H2O.CLOUD.size() == 1) == !_run_local);
    if (!_run_local) {
      _localmodel.add_processed_global(_localmodel.get_processed_local()); //move local sample counts to global ones
      _localmodel.set_processed_local(0L);
      // model averaging
      if (_chunk_node_count > 1)
        _localmodel.div(_chunk_node_count);
    } else {
      //Get ready for reduction in DeepWaterTask2
      //Just swap the local and global models
      _sharedmodel = _localmodel;
    }
    if (_sharedmodel == null)
      _sharedmodel = _localmodel;
    _localmodel = null;
  }
}
