package hex;

import water.udf.CFuncRef;
import water.udf.CFuncTask;
import water.udf.CMetricFunc;

/**
 * Custom metric scoring task.
 *
 * The task provides support to load and invoke custom model metric
 * defined via {@link water.udf.CFuncTask}. 
 *
 * @param <T>  self type
 */
public class CMetricScoringTask<T extends CMetricScoringTask<T>> extends CFuncTask<CMetricFunc, T> {

  /** Internal parameter to preserve workspace for custom metric computation */
  protected double[] customMetricWs;

  transient private CustomMetric result;

  public CMetricScoringTask(CFuncRef cFuncRef) {
    super(cFuncRef);
  }

  @Override
  protected final Class<CMetricFunc> getFuncType() {
    return CMetricFunc.class;
  }

  protected final void customMetricPerRow(double preds[], float yact[],double weight, double offset,  Model m) {
    if (func != null) {
      double[] rowR = func.map(preds, yact, weight, offset, m);
      if (customMetricWs != null) {
        customMetricWs = func.reduce(customMetricWs, rowR);
      } else {
        customMetricWs = rowR;
      }
    }
  }

  @Override
  public void reduce(T t) {
    super.reduce(t);
    reduceCustomMetric(t);
  }

  public void reduceCustomMetric(T t) {
    if (func != null) {
      if (customMetricWs == null) {
        customMetricWs = t.customMetricWs;
      } else if (t.customMetricWs == null) {
        // nop
      } else {
        customMetricWs = func.reduce(this.customMetricWs, t.customMetricWs);
      }
    }
  }

  @Override
  protected void postGlobal() {
    super.postGlobal();
    result = computeCustomMetric();
  }

  public CustomMetric computeCustomMetric() {
    if (func != null) {
      return CustomMetric.from(cFuncRef.getName(),
                                 customMetricWs != null ? func.metric(customMetricWs)
                                                        : Double.NaN);
    }
    return null;
  }

  public CustomMetric getComputedCustomMetric() {
    return result;
  }
}
