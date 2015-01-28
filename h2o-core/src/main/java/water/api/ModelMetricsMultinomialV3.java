package water.api;

import hex.ModelMetricsMultinomial;
import water.util.PojoUtils;

public class ModelMetricsMultinomialV3 extends ModelMetricsBase<ModelMetricsMultinomial, ModelMetricsMultinomialV3> {
  @Override public ModelMetricsMultinomial createImpl() {
    ModelMetricsMultinomial m = new ModelMetricsMultinomial(this.model.createImpl().get(), this.frame.createImpl().get());
    return (ModelMetricsMultinomial) m;
  }

  @Override
  public ModelMetricsMultinomial fillImpl(ModelMetricsMultinomial m) {
    PojoUtils.copyProperties(m, this, PojoUtils.FieldNaming.CONSISTENT, new String[]{"cm"});
    return m;
  }
}
