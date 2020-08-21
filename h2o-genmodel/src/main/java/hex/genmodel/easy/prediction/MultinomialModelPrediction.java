package hex.genmodel.easy.prediction;

/**
 * Binomial classification model prediction.
 */
public class MultinomialModelPrediction extends AbstractPrediction {
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
  public String[] leafNodeAssignments;  // only valid for tree-based models, null for all other mojo models
  public int[] leafNodeAssignmentIds;   // ditto, available in MOJO 1.3 and newer

  /**
   * Staged predictions of tree algorithms (prediction probabilities of trees per iteration).
   * The output structure is for tree Tt and class Cc:
   * Binomial models: [probability T1.C1, probability T2.C1, ..., Tt.C1] where Tt.C1 correspond to the the probability p0
   * Multinomial models: [probability T1.C1, probability T1.C2, ..., Tt.Cc]
   */
  public double[] stageProbabilities;
}
