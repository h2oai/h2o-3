package hex;

import water.udf.CFuncRef;
import water.udf.CFuncTask;
import water.udf.CMetricFunc;

public class CFuncScoringTask<T extends CFuncScoringTask<T>> extends CFuncTask<CMetricFunc, T> {

  /** Internal parameter to preserve workspace for custom metric computation */
  protected double[] customMetricWs;

  transient private CustomMetric result;

  public CFuncScoringTask(CFuncRef cFuncRef) {
    super(cFuncRef);
  }

  @Override
  protected final Class<CMetricFunc> getFuncType() {
    return CMetricFunc.class;
  }

  protected final void customMetricPerRow(double preds[], float yact[],double weight, double offset,  Model m) {
    if (func != null) {
      double[] rowR = func.perRow(preds, yact, weight, offset, m);
      if (customMetricWs != null) {
        customMetricWs = func.combine(customMetricWs, rowR);
      } else {
        customMetricWs = rowR;
      }
    }
  }

  @Override
  public void reduce(T t) {
    super.reduce(t);
    if (func != null) {
      if (customMetricWs == null) {
        customMetricWs = t.customMetricWs;
      } else if (t.customMetricWs == null) {
        // nop
      } else {
        customMetricWs = func.combine(this.customMetricWs, t.customMetricWs);
      }
    }
  }

  @Override
  protected void postGlobal() {
    super.postGlobal();
    if (func != null) {
      result = CustomMetric.from(cFuncRef.getName(),
                                 customMetricWs != null ? func.metric(customMetricWs)
                                                        : Double.NaN);
    } else {
      result = null;
    }
  }
  
  public CustomMetric getComputedCustomMetric() {
    return result;
  }
}
