package hex.genmodel.easy.prediction;

/**
 * CoxPH model prediction.
 */
public class CoxPHModelPrediction extends AbstractPrediction {
  /**
   * This value may be Double.NaN, which means NA (this will happen with CoxPH, for example,
   * if one of the input values for a new data point is NA).
   */
  public double value;

}
