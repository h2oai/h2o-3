package hex.tree.xgboost.exec;

import water.fvec.Frame;

public interface XGBoostExecutor extends AutoCloseable {

    void init(Frame train);

    byte[] setup();

    void update(int treeId);

    byte[] updateBooster();

}
