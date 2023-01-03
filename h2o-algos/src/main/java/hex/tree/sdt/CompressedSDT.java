package hex.tree.sdt;

import org.apache.commons.math3.util.Precision;
import water.Key;
import water.Keyed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Compressed SDT class containing tree as array.
 */
public class CompressedSDT extends Keyed<CompressedSDT> {

    /**
     * List of nodes, for each node holds either split feature index and threshold or just decision value if it is list.
     * Shape n x 3.
     * Values of second dimension: (indicator of leaf (0/1), feature index or decision value, threshold or probability).
     * For a list: (1, decision value, probability), for an internal node: (0, feature index, threshold)
     */
    private final double[][] _nodes;

    private final ArrayList<String> _listOfRules;

//    public CompressedSDT(int nodes_count) {
//        _key = Key.make("CompressedSDT" + Key.rand());
//        _nodes = new double[nodes_count][2];
//    }

    public CompressedSDT(double[][] nodes) {
        _key = Key.make("CompressedSDT" + Key.rand());
        _nodes = nodes;
        _listOfRules = new ArrayList<>();
        extractRulesStartingWithNode(0, "");
    }

    /**
     * Makes prediction by recursively evaluating the data through the tree.
     *
     * @param rowValues       - data row to find prediction for
     * @param actualNodeIndex - actual node to avaluate and then go to selected child
     * @return class label
     */
    public SDTPrediction predictRowStartingFromNode(final double[] rowValues, final int actualNodeIndex) {
        // todo - add explainability (save the chain of rules from the root to the leaf)
        int isALeaf = (int) _nodes[actualNodeIndex][0];
        double featureIndexOrValue = _nodes[actualNodeIndex][1];
        double thresholdOrProbability = _nodes[actualNodeIndex][2];
        // first value 1 means that the node is list, return prediction for the list
        if (isALeaf == 1) {
            return new SDTPrediction((int) featureIndexOrValue, thresholdOrProbability, "ruleExplanation");
        }
        if (rowValues[(int) featureIndexOrValue] < thresholdOrProbability
                || Precision.equals(rowValues[(int) featureIndexOrValue], thresholdOrProbability, Precision.EPSILON)) {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 1);
        } else {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 2);
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(_nodes).map(n -> "(" + n[0] + "," + n[1] + ")").collect(Collectors.joining(";"));
    }

    // todo - add test on this
    public void extractRulesStartingWithNode(int nodeIndex, String actualRule) {
        if(_nodes[nodeIndex][0] == 1) {
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
