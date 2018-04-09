package ml.dmlc.xgboost4j.java;

import water.H2O;
import water.MRTask;
import water.util.IcedHashMapGeneric;

/**
 * Cleans up after XGBoost training (releases any node-local data)
 */
public class XGBoostCleanupTask extends MRTask<XGBoostCleanupTask> {

  private IcedHashMapGeneric.IcedHashMapStringObject _nodeToMatrixWrapper;

  public XGBoostCleanupTask(XGBoostSetupTask setupTask) {
    _nodeToMatrixWrapper = setupTask._nodeToMatrixWrapper;
  }

  @Override
  protected void setupLocal() {
    if (H2O.ARGS.client || _nodeToMatrixWrapper == null) {
      return;
    }

    PersistentDMatrix.Wrapper wrapper = (PersistentDMatrix.Wrapper) _nodeToMatrixWrapper.get(H2O.SELF.toString());
    if (wrapper != null)
      wrapper.get().dispose();
  }

  public static void cleanUp(XGBoostSetupTask setupTask) {
    new XGBoostCleanupTask(setupTask).doAllNodes();
  }

}
