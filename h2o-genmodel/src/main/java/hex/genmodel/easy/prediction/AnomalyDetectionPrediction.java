package hex.genmodel.easy.prediction;

public class AnomalyDetectionPrediction extends AbstractPrediction {

  /**
   * Only available when MojoModel has contamination parameter defined otherwise is null.
   */
  public Boolean isAnomaly;

  /**
   * The raw number that an algorithm is using to count final anomaly score.
   *
   * E.g. for Isolation Forest this number is mean path length of data in the trees. Smaller number means more anomalous point, higher number means more normal point.
   */
  public double score;

  /**
   * Higher number means more anomalous point, smaller number means more normal point.
   */
  public double normalizedScore;

  /**
   * Only valid for tree-based models, null for all other mojo models.
   */
  public String[] leafNodeAssignments;

  /**
   * Ditto, available in MOJO 1.3 and newer
   */
  public int[] leafNodeAssignmentIds;

  /**
   * Staged predictions of tree algorithms (prediction probabilities of trees per iteration).
   * The output structure is for tree Tt and class Cc:
   * Binomial models: [probability T1.C1, probability T2.C1, ..., Tt.C1] where Tt.C1 correspond to the the probability p0
   * Multinomial models: [probability T1.C1, probability T1.C2, ..., Tt.Cc]
   */
  public double[] stageProbabilities;

  @SuppressWarnings("unused")
  public AnomalyDetectionPrediction() {
  }
  
  public AnomalyDetectionPrediction(double[] preds) {
    if (preds.length == 3) {
      isAnomaly = preds[0] == 1;
      normalizedScore = preds[1];
      score = preds[2];
    } else {
      normalizedScore = preds[0];
      score = preds[1];
    }
  }

  public double[] toPreds() {
    if (isAnomaly != null) {
      return new double[] {isAnomaly ? 1 : 0, normalizedScore, score};
    } else {
      return new double[] {normalizedScore, score};
    }
  }
  
}
