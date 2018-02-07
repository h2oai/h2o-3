package hex.tree.xgboost;

import water.ExtensionManager;
import water.MRTask;

public class XGBoostExtensionCheck extends MRTask<XGBoostExtensionCheck> {
    boolean enabled;

    @Override
    protected void setupLocal() {
        super.setupLocal();
        enabled = ExtensionManager.getInstance().isCoreExtensionEnabled(XGBoostExtension.NAME);
    }

    @Override
    public void reduce(XGBoostExtensionCheck mrt) {
        super.reduce(mrt);
        enabled = enabled && mrt.enabled;
    }
}
