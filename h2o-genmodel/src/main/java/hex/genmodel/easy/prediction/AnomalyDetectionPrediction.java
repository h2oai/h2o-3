package hex.genmodel.easy.prediction;

public class AnomalyDetectionPrediction extends AbstractPrediction {

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

  public Boolean isAnomaly;
  public double score;
  public double normalizedScore;

  public String[] leafNodeAssignments;  // only valid for tree-based models, null for all other mojo models
  public int[] leafNodeAssignmentIds;   // ditto, available in MOJO 1.3 and newer

  /**
   * Staged predictions of tree algorithms (prediction probabilities of trees per iteration).
   * The output structure is for tree Tt and class Cc:
   * Binomial models: [probability T1.C1, probability T2.C1, ..., Tt.C1] where Tt.C1 correspond to the the probability p0
   * Multinomial models: [probability T1.C1, probability T1.C2, ..., Tt.Cc]
   */
  public double[] stageProbabilities;

  public double[] toPreds() {
    if (isAnomaly != null) {
      return new double[] {isAnomaly ? 1 : 0, normalizedScore, score};
    } else {
      return new double[] {normalizedScore, score};
    }
  }
  
}
