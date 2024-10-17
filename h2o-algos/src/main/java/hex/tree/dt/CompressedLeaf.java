package hex.tree.dt;


import java.util.Arrays;
import java.util.stream.Collectors;

public class CompressedLeaf extends AbstractCompressedNode {
    private final double _decisionValue;
    private final double[] _probabilities;


    public CompressedLeaf(double decisionValue, double[] probabilities) {
        super();
        _decisionValue = decisionValue;
        _probabilities = probabilities;
    }

    public double getDecisionValue() {
        return _decisionValue;
    }

    public double[] getProbabilities() {
        return _probabilities;
    }

    @Override
    public String toString() {
        return "(leaf: " + _decisionValue + "; " 
                + Arrays.stream(_probabilities).mapToObj(Double::toString)
                .collect(Collectors.joining(", ")) + ")";
    }
}
