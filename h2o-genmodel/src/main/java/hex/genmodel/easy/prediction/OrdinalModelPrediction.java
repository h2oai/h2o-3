package hex.genmodel.easy.prediction;

/**
 * Ordinal classification model prediction.
 */
public class OrdinalModelPrediction extends AbstractPrediction {
  /**
   * Index number of the predicted class (aka categorical or factor level) in the response column.
   */
  public int labelIndex;

  /**
   * Label of the predicted level.
   */
  public String label;

  /**
   * This array has an element for each class (aka categorical or factor level) in the response column.
   *
   * The array corresponds to the level names returned by:
   * <pre>
   * model.getDomainValues(model.getResponseIdx())
   * </pre>
   * "Domain" is the internal H2O term for level names.
   *
   * The values in this array may be Double.NaN, which means NA.
   * If they are valid numeric values, then they will sum up to 1.0.
   */
  public double[] classProbabilities;
}
