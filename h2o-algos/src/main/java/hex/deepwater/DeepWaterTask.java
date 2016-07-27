package hex.deepwater;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import static water.gpu.util.img2pixels;
import water.parser.BufferedString;
import water.util.Log;

import java.io.IOException;

public class DeepWaterTask extends MRTask<DeepWaterTask> {
  final private boolean _training;
  private DeepWaterModelInfo _localmodel; //per-node state (to be reduced)
  private DeepWaterModelInfo _sharedmodel; //input/output
  int _chunk_node_count = 1;
  float _useFraction;
  boolean _shuffle;
  int _weightIdx =-1;

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
  public DeepWaterTask(DeepWaterModelInfo inputModel, float fraction) {
    _training=true;
    _sharedmodel = inputModel;
    _useFraction=fraction;
    _shuffle = model_info().get_params()._shuffle_training_data;
  }

  /**
   * Transfer ownership from global (shared) model to local model which will be worked on
   */
  @Override protected void setupLocal(){
    assert(_localmodel == null);
    super.setupLocal();
    _localmodel = _sharedmodel;
    _sharedmodel = null;
    _localmodel.set_processed_local(0);
    _weightIdx=_fr.find(_localmodel.get_params()._weights_column);
  }

  @Override
  synchronized
  public void map(Chunk[] chks) {
    BufferedString bs = new BufferedString();
    int width = 224;
    int height = 224;
    for (int i=0;i<chks[0]._len;++i) {
      double weight = _weightIdx==-1?1:chks[_weightIdx].atd(i);
      if (weight==0)
        continue;
      String file = chks[0].atStr(bs, i).toString();
      Log.info("Training on image: " + file);
      float[] raw;
      float[] label = new float[1];
      try {
        raw = img2pixels(file, width, height);
        label[0] = (float)chks[chks.length-1].atd(i); //FIXME: mini-batch and use synchronized for each minibatch only
        _localmodel._imageTrain.train(raw, label);
        _localmodel.add_processed_local(_localmodel.get_params()._mini_batch_size);
      } catch (IOException e) {
        e.printStackTrace();
      }
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
   * @param other
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


  static long _lastWarn;
  static long _warnCount;
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
      _localmodel.set_processed_local(0l);
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
