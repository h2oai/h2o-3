package hex.deeplearning;

import water.Key;
import water.MRTask;
import water.fvec.Frame;

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
  public DeepLearningTask2(Key jobKey, Frame train, DeepLearningModelInfo model_info, float sync_fraction, int iteration) {
    assert(sync_fraction > 0);
    _jobKey = jobKey;
    _fr = train;
    _sharedmodel = model_info;
    _sync_fraction = sync_fraction;
    _iteration = iteration;
  }

  /**
   * Returns the aggregated DeepLearning model that was trained by all nodes (over all the training data)
   * @return model_info object
   */
  public DeepLearningModelInfo model_info() { return _sharedmodel; }

  final private Key _jobKey;
  final private Frame _fr;
  private DeepLearningModelInfo _sharedmodel;
  final private float _sync_fraction;
  private DeepLearningTask _res;
  private final int _iteration;

  /**
   * Do the local computation: Perform one DeepLearningTask (with run_local=true) iteration.
   * Pass over all the data (will be replicated in dfork() here), and use _sync_fraction random rows.
   * This calls DeepLearningTask's reduce() between worker threads that update the same local model_info via Hogwild!
   * Once the computation is done, reduce() will be called
   */
  @Override
  public void setupLocal() {
    super.setupLocal();
    _res = new DeepLearningTask(_jobKey, _sharedmodel, _sync_fraction, _iteration, this);
    addToPendingCount(1);
    _res.dfork(null, _fr, true /*run_local*/);
  }

  /**
   * Reduce between worker nodes, with network traffic (if greater than 1 nodes)
   * After all reduce()'s are done, postGlobal() will be called
   * @param drt task to reduce
   */
  @Override
  public void reduce(DeepLearningTask2 drt) {
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
    assert(_res.model_info().get_params()._replicate_training_data);
    super.postGlobal();
    // model averaging (DeepLearningTask only computed the per-node models, each on all the data)
    _res.model_info().div(_res._chunk_node_count);
    _res.model_info().add_processed_global(_res.model_info().get_processed_local()); //switch from local counters to global counters
    _res.model_info().set_processed_local(0l);
    DeepLearningModelInfo nodeAverageModel = _res.model_info();
    if (nodeAverageModel.get_params()._elastic_averaging)
      _sharedmodel = DeepLearningModelInfo.timeAverage(nodeAverageModel);
    else
      _sharedmodel = nodeAverageModel;
  }
}
