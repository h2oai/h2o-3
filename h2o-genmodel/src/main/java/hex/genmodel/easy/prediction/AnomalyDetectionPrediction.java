package hex.genmodel.easy.prediction;

public class AnomalyDetectionPrediction extends AbstractPrediction {

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

}
