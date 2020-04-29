package water.udf.metric;

import org.junit.Ignore;

import hex.Model;
import water.udf.CMetricFunc;
import water.util.ArrayUtils;

@Ignore("Support for tests, but no actual tests here")
public class MEACustomMetric implements CMetricFunc {

  @Override
  public double[] map(double[] preds, float[] yact, double weight, double offset, Model m) {
    return new double[] { Math.abs(preds[0] - yact[0]), 1};
  }

  @Override
  public double[] reduce(double[] l, double[] r) {
    ArrayUtils.add(l, r);
    return l;
  }

  @Override
  public double metric(double[] r) {
    return r[0]/r[1];
  }
}
