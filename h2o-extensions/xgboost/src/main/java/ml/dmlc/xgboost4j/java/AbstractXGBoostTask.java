package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.XGBoostModel;
import water.*;

abstract class AbstractXGBoostTask<T extends MRTask<T>> extends MRTask<T> {

  final Key<XGBoostModel> _modelKey;
  private final boolean[] _hasDMatrix;

  AbstractXGBoostTask(AbstractXGBoostTask<?> setupTask) {
    this(setupTask._modelKey, setupTask._hasDMatrix);
  }

  AbstractXGBoostTask(Key<XGBoostModel> modelKey, boolean[] hasDMatrix) {
    _modelKey = modelKey;
    _hasDMatrix = hasDMatrix;
  }

  @Override
  protected final void setupLocal() {
    assert _fr == null : "MRTask invoked on a Frame with no intention to run map() on Chunks might not invoke reduce(); " +
            "use doAllNodes() to make sure reduce() will be called.";
    if (H2O.ARGS.client) {
      return;
    }
    if (!_hasDMatrix[H2O.SELF.index()])
      return;
    // We need to verify that the xgboost is available on the remote node
    if (!ExtensionManager.getInstance().isCoreExtensionEnabled(XGBoostExtension.NAME)) {
      throw new IllegalStateException("XGBoost is not available on the node " + H2O.SELF);
    }
    // Do the work
    execute();
  }

  abstract void execute();

  /**
   * Alias to doAllNodes() - the XGBoost task will actually run only on selected nodes. We use doAllNodes() to
   * make sure the reduce() operations defined on the tasks will be invoked even if there was no work done on the node
   * from MRTask's point of view.
   */
  public T run() {
    return doAllNodes();
  }

  H2ONode getBoosterNode() {
    for (int i = 0; i < H2O.CLOUD.size(); i++) {
      if (_hasDMatrix[i])
        return H2O.CLOUD._memary[i];
    }
    throw new IllegalStateException("No node of the cluster is holding a Booster");
  }

}
