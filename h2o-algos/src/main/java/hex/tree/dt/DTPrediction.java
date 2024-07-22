package hex.tree.dt;

public class DTPrediction {
    public int classPrediction;
    public double[] probabilities;
    public String ruleExplanation;

    public DTPrediction(int classPrediction, double[] probabilities, String ruleExplanation) {
        this.classPrediction = classPrediction;
        this.probabilities = probabilities;
        this.ruleExplanation = ruleExplanation;
    }
}
