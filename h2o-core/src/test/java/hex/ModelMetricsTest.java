package hex;

import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.*;

public class ModelMetricsTest extends TestBase {

  @Test
  public void testEmptyModelAUC() {
    ModelMetricsBinomial.MetricBuilderBinomial mbb =
            new ModelMetricsBinomial.MetricBuilderBinomial(new String[]{"yes", "yes!!"});
    ModelMetrics mm = mbb.makeModelMetrics(null, null, null, null);

    assertTrue(mm instanceof ModelMetricsBinomial);
    assertTrue(Double.isNaN(((ModelMetricsBinomial) mm).auc()));
    assertTrue(Double.isNaN(ModelMetrics.getMetricFromModelMetric(mm, "auc")));
  }

}
