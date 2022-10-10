package hex.tree.xgboost.task;

import hex.tree.xgboost.EvalMetric;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XGBoostUpdaterTest {

    @Test
    public void testParseEvalMetric() {
        // ok
        EvalMetric em = XGBoostUpdater.parseEvalMetric("error@0.75", "[1] error@0.75: " + Math.PI);
        assertEquals(new EvalMetric("error@0.75", Math.PI), em);
        // no separator
        EvalMetric em_ns = XGBoostUpdater.parseEvalMetric("auc", "invalid");
        assertEquals(new EvalMetric("auc", Double.NaN), em_ns);
        // invalid number
        EvalMetric em_in = XGBoostUpdater.parseEvalMetric("mae", "[0] mae: a.0");
        assertEquals(new EvalMetric("mae", Double.NaN), em_in);
    }
}
