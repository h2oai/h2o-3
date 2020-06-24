package hex.tree.xgboost.exec;

public interface XGBoostExecutor extends AutoCloseable {

    byte[] setup();

    void update(int treeId);

    byte[] updateBooster();

}
