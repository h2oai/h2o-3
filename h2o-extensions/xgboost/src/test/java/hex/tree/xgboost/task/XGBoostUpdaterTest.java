package hex.tree.xgboost.task;

import hex.tree.xgboost.EvalMetric;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XGBoostUpdaterTest {

    @Test
    public void testParseEvalMetric() {
        // ok
        EvalMetric em = XGBoostUpdater.parseEvalMetric("error@0.75", false, "[1]\terror@0.75: " + Math.PI);
        assertEquals(new EvalMetric("error@0.75", Math.PI, Double.NaN), em);
        // no separator
        EvalMetric em_ns = XGBoostUpdater.parseEvalMetric("auc", true, "invalid");
        assertEquals(new EvalMetric("auc", Double.NaN, Double.NaN), em_ns);
        // invalid number
        EvalMetric em_in = XGBoostUpdater.parseEvalMetric("mae", false, "[0]\tmae: a.0");
        assertEquals(new EvalMetric("mae", Double.NaN, Double.NaN), em_in);
        // with validation metric value
        EvalMetric em_vm = XGBoostUpdater.parseEvalMetric("logloss", true, "[0]\ttrain-logloss:0.519498\tvalid-logloss:0.519498");
        assertEquals(new EvalMetric("logloss", 0.519498, 0.519498), em_vm);
    }
}
