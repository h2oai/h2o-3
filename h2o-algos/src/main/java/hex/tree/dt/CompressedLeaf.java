package hex.tree.dt;


public class CompressedLeaf extends AbstractCompressedNode {
    private final double _decisionValue;
    private final double _probability;


    public CompressedLeaf(double decisionValue, double probabilities) {
        super();
        _decisionValue = decisionValue;
        _probability = probabilities;
    }

    public double getDecisionValue() {
        return _decisionValue;
    }

    public double getProbabilities() {
        return _probability;
    }

    @Override
    public String toString() {
        return "(leaf: " + _decisionValue + ", " + _probability + ", " + (1- _probability) + ")";
    }
}
