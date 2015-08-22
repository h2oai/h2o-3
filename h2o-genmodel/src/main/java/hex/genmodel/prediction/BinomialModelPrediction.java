package hex.genmodel.prediction;

/**
 * Binomial classification model prediction.
 *
 * GLM logistic regression (GLM family "binomial") also falls into this category.
 */
public class BinomialModelPrediction extends AbstractPrediction {
  /**
   * 0 or 1.
   */
  public int labelIndex;

  /**
   * Label of the predicted level.
   */
  public String label;

  /**
   * This array of length two has the class probability for each class (aka categorical or factor level) in the
   * response column.
   *
   * The array corresponds to the level names returned by:
   * <pre>
   * model.getDomainValues(model.getResponseIdx())
   * </pre>
   * "Domain" is the internal H2O term for level names.
   *
   * The values in this array may be Double.NaN, which means NA (this will happen with GLM, for example,
   * if one of the input values for a new data point is NA).
   * If they are valid numeric values, then they will sum up to 1.0.
   */
  public double[] classProbabilities;
}
