package water.udf;

import hex.Model;

/**
 * Custom metric evaluation function.
 *
 * The function has 3 parts:
 *   - perRow:  invocation
 *   - combine: combine perRow results
 *   - metric: compute final metric
 */
public interface CMetricFunc extends CFunc {
  double[] perRow(double preds[], float yact[],double weight, double offset,  Model m);
  double[] combine(double[] l, double r[]);
  double metric(double[] r);
}
