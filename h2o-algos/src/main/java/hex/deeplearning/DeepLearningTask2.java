package hex.deeplearning;

import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Frame;
import water.util.Log;

/**
 * DRemoteTask-based Deep Learning.
 * Every node has access to all the training data which leads to optimal CPU utilization and training accuracy IFF the data fits on every node.
 */
public class DeepLearningTask2 extends MRTask<DeepLearningTask2> {
  /**
   * Construct a DeepLearningTask2 where every node trains on the entire training dataset
   * @param jobKey Job ID
   * @param train Frame containing training data
   * @param model_info Initial DeepLearningModelInfo (weights + biases)
   * @param sync_fraction Fraction of the training data to use for one SGD iteration
   */
  public DeepLearningTask2(Key jobKey, Frame train, DeepLearningModel.DeepLearningModelInfo model_info, float sync_fraction) {
    assert(sync_fraction > 0);
    _jobKey = jobKey;
    _fr = train;
    _model_info = model_info;
    _sync_fraction = sync_fraction;
  }

  /**
   * Returns the aggregated DeepLearning model that was trained by all nodes (over all the training data)
   * @return model_info object
   */
  public DeepLearningModel.DeepLearningModelInfo model_info() {
    assert(_res.model_info() != null);
    assert(_res.model_info().get_params()._replicate_training_data);
    return _res.model_info();
  }

  final private Key _jobKey;
  final private Frame _fr;
  final private DeepLearningModel.DeepLearningModelInfo _model_info;
  final private float _sync_fraction;
  private DeepLearningTask _res;

  /**
   * Do the local computation: Perform one DeepLearningTask (with run_local=true) iteration.
   * Pass over all the data (will be replicated in dfork() here), and use _sync_fraction random rows.
   * This calls DeepLearningTask's reduce() between worker threads that update the same local model_info via Hogwild!
   * Once the computation is done, reduce() will be called
   */
  @Override
  public void setupLocal() {
//    Log.info("DLT2: setupLocal()");
    assert(_model_info.get_params()._replicate_training_data);
    super.setupLocal();
    _res = new DeepLearningTask(_jobKey, _model_info, _sync_fraction);
    assert(_res.model_info().get_params()._replicate_training_data);
    _res.setCompleter(this);
    assert(_model_info == _res.model_info()); //returns shared model (average) to public
    addToPendingCount(1);
    _res.asyncExec(0, _fr, true /*run_local*/);
    assert(_res.model_info().get_params()._replicate_training_data);
  }

  /**
   * Reduce between worker nodes, with network traffic (if greater than 1 nodes)
   * After all reduce()'s are done, postGlobal() will be called
   * @param drt task to reduce
   */
  @Override
  public void reduce(DeepLearningTask2 drt) {
//    Log.info("DLT2: reduce()");
    assert(_res.model_info().get_params()._replicate_training_data);
    if (_res == null) _res = drt._res;
    else {
      _res._chunk_node_count += drt._res._chunk_node_count;
      _res.model_info().add(drt._res.model_info()); //add models, but don't average yet
    }
    assert(_res.model_info().get_params()._replicate_training_data);
  }

  /**
   * Finish up the work after all nodes have reduced their models via the above reduce() method.
   * All we do is average the models and add to the global training sample counter.
   * After this returns, model_info() can be queried for the updated model.
   */
  @Override
  protected void postGlobal() {
//    Log.info("DLT2: postGlobal()");
    assert(_res.model_info().get_params()._replicate_training_data);
    super.postGlobal();
    if (_res._chunk_node_count > 1) _res.model_info().div(_res._chunk_node_count); //model averaging
    _res.model_info().add_processed_global(_res.model_info().get_processed_local()); //switch from local counters to global counters
    _res.model_info().set_processed_local(0l);
    // time- and node-average of the weights
    if (!_res.model_info().get_params()._replicate_training_data && H2O.CLOUD.size() > 1 && _res.consensusADMM) {
      Log.info("DLT2: Adding per-node model averages to consensus model.");
      DeepLearningModel.DeepLearningModelInfo moving_avg = (DeepLearningModel.DeepLearningModelInfo)_res.model_info().clone();//FIXME //HACK
      moving_avg.set_processed_local(0); //to prevent adding samples to _res.model_info() in add() below
      moving_avg.div(1.f/0.9f); //multiply by 0.9 - time average
      _res.model_info().div(10f); //add 1/10 of the averaged per-node models
      _res.model_info().add(moving_avg);
    }
    assert(_res.model_info().get_params()._replicate_training_data);
  }

}
