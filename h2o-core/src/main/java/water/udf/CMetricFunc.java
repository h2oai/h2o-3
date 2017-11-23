package water.udf;

import hex.Model;

/**
 * Custom metric evaluation function.
 *
 * The function has 3 parts:
 *   - map:  the function is executed per row and return computation state in form of array of doubles
 *   - reduce: combine 2 map results
 *   - metric: compute final metric based on the computed state
 */
public interface CMetricFunc extends CFunc {

  /**
   * Compute temporary state for given row of data.
   *
   * The method is invoked per row represented by prediction and actual response
   * and return "temporary computation state that is
   * later combined together with other map-results to form final value of metric.
   *
   * @param preds  predicted response value
   * @param yact   actual response value
   * @param weight  weight of row
   * @param offset  offset of row
   * @param m  model
   * @return  temporary result in form of array of doubles.
   */
  double[] map(double preds[], float yact[],double weight, double offset,  Model m);

  /**
   * Combine two map-call results together.
   * 
   * @param l a result of map call
   * @param r a result of map call
   * @return  combined results
   */
  double[] reduce(double[] l, double r[]);

  /**
   * Get value of metric for given computation state.
   * @param r  computation state
   * @return  value of metric
   */
  double metric(double[] r);
}
