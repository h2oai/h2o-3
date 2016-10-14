package hex.deepwater;

import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Frame;

/**
 * DRemoteTask-based Deep Learning.
 * Every node has access to all the training data which leads to optimal CPU utilization and training accuracy IFF the data fits on every node.
 */
class DeepWaterTask2 extends MRTask<DeepWaterTask2> {
  /**
   * Construct a DeepWaterTask2 where every node trains on the entire training dataset
   * @param jobKey Job ID
   * @param train Frame containing training data
   * @param model_info Initial DeepWaterModelInfo (weights + biases)
   * @param sync_fraction Fraction of the training data to use for one SGD iteration
   */
  DeepWaterTask2(Key jobKey, Frame train, DeepWaterModelInfo model_info, float sync_fraction, int iteration) {
    assert(sync_fraction > 0);
    _jobKey = jobKey;
    _fr = train;
    _sharedmodel = model_info;
    _sync_fraction = sync_fraction;
  }

  /**
   * Returns the aggregated DeepWater model that was trained by all nodes (over all the training data)
   * @return model_info object
   */
  public DeepWaterModelInfo model_info() { return _sharedmodel; }

  final private Key _jobKey;
  final private Frame _fr;
  private DeepWaterModelInfo _sharedmodel;
  final private float _sync_fraction;
  private DeepWaterTask _res;

  /**
   * Do the local computation: Perform one DeepWaterTask (with run_local=true) iteration.
   * Pass over all the data (will be replicated in dfork() here), and use _sync_fraction random rows.
   * This calls DeepWaterTask's reduce() between worker threads that update the same local model_info via Hogwild!
   * Once the computation is done, reduce() will be called
   */
  @Override
  public void setupLocal() {
    super.setupLocal();
    _res = new DeepWaterTask(_sharedmodel, _sync_fraction, (Job)_jobKey.get());
    addToPendingCount(1);
    _res.dfork(null, _fr, true /*run_local*/);
  }

  /**
   * Reduce between worker nodes, with network traffic (if greater than 1 nodes)
   * After all reduce()'s are done, postGlobal() will be called
   * @param drt task to reduce
   */
  @Override
  public void reduce(DeepWaterTask2 drt) {
    if (_res == null) _res = drt._res;
    else {
//      _res._chunk_node_count += drt._res._chunk_node_count;
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
    assert(_res.model_info().get_params()._replicate_training_data);
    super.postGlobal();
    // model averaging (DeepWaterTask only computed the per-node models, each on all the data)
//    _res.model_info().div(_res._chunk_node_count);
    _res.model_info().add_processed_global(_res.model_info().get_processed_local()); //switch from local counters to global counters
    _res.model_info().set_processed_local(0L);
    _sharedmodel = _res.model_info();
  }
}
