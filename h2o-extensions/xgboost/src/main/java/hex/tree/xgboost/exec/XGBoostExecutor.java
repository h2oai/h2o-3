package hex.tree.xgboost.exec;

import hex.CustomMetric;
import hex.tree.xgboost.EvalMetric;

public interface XGBoostExecutor extends AutoCloseable {

    byte[] setup();

    void update(int treeId);

    byte[] updateBooster();

    /**
     * Retrieves the current value of Evaluation Metric on the training dataset.
     * Note: this API is likely going to change in the future when we get capability
     * of calculating eval metric both on training and validation frames.
     * 
     * @return instance of Custom Metric or null if no custom metric was defined
     */
    EvalMetric getEvalMetricTrain();

}
