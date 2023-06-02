package hex.tree.xgboost.exec;

import hex.tree.xgboost.EvalMetric;

public interface XGBoostExecutor extends AutoCloseable {

    byte[] setup();

    void update(int treeId);

    byte[] updateBooster();

    /**
     * Retrieves the value of the evaluation metric both for training and validation dataset.
     * @return instance of EvalMetric if "eval_metric" was defined, null otherwise
     */
    EvalMetric getEvalMetric();

}
