package hex.tree.dt;

import org.apache.commons.math3.util.Precision;
import water.Key;
import water.Keyed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Compressed DT class containing tree as array.
 */
public class CompressedDT extends Keyed<CompressedDT> {

    /**
     * List of nodes, for each node holds either split feature index and threshold or just decision value if it is list.
     * Shape n x 3.
     * Values of second dimension: (indicator of leaf (0/1), feature index or decision value, threshold or probability).
     * For a leaf: (1, decision value, probability), for an internal node: (0, feature index, threshold)
     */
    private final double[][] _nodes;

    private final ArrayList<String> _listOfRules;


    public CompressedDT(double[][] nodes) {
        _key = Key.make("CompressedDT" + Key.rand());
        _nodes = nodes;
        _listOfRules = new ArrayList<>();
        extractRulesStartingWithNode(0, "");
    }

    /**
     * Makes prediction by recursively evaluating the data through the tree.
     *
     * @param rowValues       - data row to find prediction for
     * @param actualNodeIndex - actual node to evaluate and then go to selected child
     * @return class label
     */
    public DTPrediction predictRowStartingFromNode(final double[] rowValues, final int actualNodeIndex, String ruleExplanation) {
        int isALeaf = (int) _nodes[actualNodeIndex][0];
        double featureIndexOrValue = _nodes[actualNodeIndex][1];
        double thresholdOrProbability = _nodes[actualNodeIndex][2];
        // first value 1 means that the node is list, return prediction for the list
        if (isALeaf == 1) {
            return new DTPrediction((int) featureIndexOrValue, thresholdOrProbability,
                    ruleExplanation + " -> (" + featureIndexOrValue
                            + ", probabilities: " + thresholdOrProbability + ", " + (1 - thresholdOrProbability) + ")");
        }
        if (!ruleExplanation.isEmpty()) {
            ruleExplanation += " and ";
        }
        if (rowValues[(int) featureIndexOrValue] < thresholdOrProbability
                || Precision.equals(rowValues[(int) featureIndexOrValue], thresholdOrProbability, Precision.EPSILON)) {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 1,
                    ruleExplanation + "(x" + featureIndexOrValue + " <= " + thresholdOrProbability + ")");
        } else {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 2,
                    ruleExplanation + "(x" + featureIndexOrValue + " > " + thresholdOrProbability + ")");
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(_nodes).map(n -> "(" + n[0] + "," + n[1] + "," + n[2] + ")").collect(Collectors.joining(";"));
    }

    public void extractRulesStartingWithNode(int nodeIndex, String actualRule) {
        if (_nodes[nodeIndex][0] == 1) {
            // if node is a list, add the rule to the list and return
            _listOfRules.add(actualRule + " -> (" + _nodes[nodeIndex][1] + ", " + _nodes[nodeIndex][2] + ")");
            return;
        }

        actualRule = actualRule.isEmpty() ? actualRule : actualRule + " and ";
        // proceed to the left branch
        extractRulesStartingWithNode(2 * nodeIndex + 1, actualRule +
                "(x" + _nodes[nodeIndex][1] + " <= " + _nodes[nodeIndex][2] + ")");
        // proceed to the right branch
        extractRulesStartingWithNode(2 * nodeIndex + 2, actualRule +
                "(x" + _nodes[nodeIndex][1] + " > " + _nodes[nodeIndex][2] + ")");
    }

    public List<String> getListOfRules() {
        return _listOfRules;
    }

    public double[][] getNodes() {
        return _nodes;
    }

}
