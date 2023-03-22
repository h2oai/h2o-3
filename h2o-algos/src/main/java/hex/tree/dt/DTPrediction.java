package hex.tree.dt;

public class DTPrediction {
    public int classPrediction;
    public double probability;
    public String ruleExplanation;

    public DTPrediction(int classPrediction, double probability, String ruleExplanation) {
        this.classPrediction = classPrediction;
        this.probability = probability;
        this.ruleExplanation = ruleExplanation;
    }
}
