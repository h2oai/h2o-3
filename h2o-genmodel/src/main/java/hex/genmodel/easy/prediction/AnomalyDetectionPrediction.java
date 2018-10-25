package hex.genmodel.easy.prediction;

public class AnomalyDetectionPrediction extends AbstractPrediction {

  public double score;
  public double normalizedScore;

  public String[] leafNodeAssignments;  // only valid for tree-based models, null for all other mojo models
  public int[] leafNodeAssignmentIds;   // ditto, available in MOJO 1.3 and newer

}
