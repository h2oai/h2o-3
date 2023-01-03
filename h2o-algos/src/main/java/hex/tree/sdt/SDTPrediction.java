package hex.tree.sdt;

public class SDTPrediction {
  public int classPrediction;
  public double probability;
  public String ruleExplanation;

  public SDTPrediction(int classPrediction, double probability, String ruleExplanation) {
    this.classPrediction = classPrediction;
    this.probability = probability;
    this.ruleExplanation = ruleExplanation;
  }
}
