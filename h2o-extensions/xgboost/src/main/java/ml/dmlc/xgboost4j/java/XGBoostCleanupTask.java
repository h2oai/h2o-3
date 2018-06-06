package ml.dmlc.xgboost4j.java;

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
  }

  public static void cleanUp(XGBoostSetupTask setupTask) {
    new XGBoostCleanupTask(setupTask).doAllNodes();
  }

}
