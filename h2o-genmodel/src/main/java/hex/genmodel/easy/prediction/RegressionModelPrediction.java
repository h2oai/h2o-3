package hex.genmodel.easy.prediction;

/**
 * Regression model prediction.
 */
public class RegressionModelPrediction extends AbstractPrediction {
  /**
   * This value may be Double.NaN, which means NA (this will happen with GLM, for example,
   * if one of the input values for a new data point is NA).
   */
  public double value;
  public String[] leafNodeAssignments;  // only valid for GBM or DRF, null for all other mojo models
  public int[] leafNodeAssignmentIds;   // ditto, available in MOJO 1.3 and newer

  /**
   * Staged predictions of tree algorithms (prediction probabilities of trees per iteration).
   * The output structure is for tree Tt and class Cc:
   * Binomial models: [probability T1.C1, probability T2.C1, ..., Tt.C1] where Tt.C1 correspond to the the probability p0
   * Multinomial models: [probability T1.C1, probability T1.C2, ..., Tt.Cc]
   */
  public double[] stageProbabilities;
}
