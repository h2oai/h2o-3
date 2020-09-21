package hex.tree.xgboost.task;

import hex.tree.xgboost.matrix.RemoteMatrixLoader;

/**
 * Cleans up after XGBoost training
 */
public class XGBoostCleanupTask extends AbstractXGBoostTask<XGBoostCleanupTask> {

  private XGBoostCleanupTask(XGBoostSetupTask setupTask) {
    super(setupTask);
  }

  @Override
  protected void execute() {
    XGBoostUpdater.terminate(_modelKey);
    RemoteMatrixLoader.cleanup(_modelKey.toString());
  }

  public static void cleanUp(XGBoostSetupTask setupTask) {
    new XGBoostCleanupTask(setupTask).doAllNodes();
  }

}
