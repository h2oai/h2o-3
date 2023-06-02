package water.api.schemas3;

import hex.ModelMetricsMultinomialGLMGeneric;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsMultinomialGLMGenericV3 extends ModelMetricsMultinomialV3<ModelMetricsMultinomialGLMGeneric, ModelMetricsMultinomialGLMGenericV3> {
  @API(help="residual deviance",direction = Direction.OUTPUT)
  public double residual_deviance;

  @API(help="null deviance",direction = Direction.OUTPUT)
  public double null_deviance;

  @API(help="AIC",direction = Direction.OUTPUT)
  public double AIC;
  
  @API(help="log likelihood",direction = Direction.OUTPUT)
  public double loglikelihood;

  @API(help="null DOF", direction= Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help="residual DOF", direction= Direction.OUTPUT)
  public long residual_degrees_of_freedom;

  @API(direction = API.Direction.OUTPUT, help="coefficients_table")
  public TwoDimTableV3 coefficients_table; // Originally not part of metrics, put here to avoid GenericOutput having multiple output classes.

  @Override
  public ModelMetricsMultinomialGLMGenericV3 fillFromImpl(ModelMetricsMultinomialGLMGeneric mms) {
    super.fillFromImpl(mms);
    this.AIC = mms._AIC;
    this.loglikelihood = mms._loglikelihood;
    this.residual_deviance = mms._resDev;
    this.null_deviance = mms._nullDev;
    this.null_degrees_of_freedom = mms._nullDegreesOfFreedom;
    this.residual_degrees_of_freedom = mms._residualDegreesOfFreedom;
    this.coefficients_table = mms._coefficients_table != null ? new TwoDimTableV3().fillFromImpl(mms._coefficients_table) : null;
    this.r2 = mms.r2();

    if (mms._hit_ratio_table != null) {
      hit_ratio_table = new TwoDimTableV3(mms._hit_ratio_table);
    }

    if (null != mms._confusion_matrix_table) {
      final ConfusionMatrixV3 convertedConfusionMatrix = new ConfusionMatrixV3();
      convertedConfusionMatrix.table = new TwoDimTableV3().fillFromImpl(mms._confusion_matrix_table);
      this.cm = convertedConfusionMatrix;
    }
    
    return this;
  }

}
