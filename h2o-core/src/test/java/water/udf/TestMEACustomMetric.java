package water.udf;

import org.junit.Ignore;

import hex.Model;
import water.util.ArrayUtils;

@Ignore("Support for tests, but no actual tests here")
public class TestMEACustomMetric implements CMetricFunc {

  @Override
  public double[] perRow(double[] preds, float[] yact, double weight, double offset, Model m) {
    return new double[] { Math.abs(preds[0] - yact[0]), 1};
  }

  @Override
  public double[] combine(double[] l, double[] r) {
    ArrayUtils.add(l, r);
    return l;
  }

  @Override
  public double metric(double[] r) {
    return r[0]/r[1];
  }
}
