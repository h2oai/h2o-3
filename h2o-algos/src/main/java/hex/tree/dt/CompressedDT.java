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
    private final AbstractCompressedNode[] _nodesObj;

    private final ArrayList<String> _listOfRules;


    public CompressedDT(double[][] nodes) {
        _key = Key.make("CompressedDT" + Key.rand());
        _nodes = nodes;
        _nodesObj = null;
        _listOfRules = new ArrayList<>();
        extractRulesStartingWithNode(0, "");
    }

    public CompressedDT(AbstractCompressedNode[] nodes) {
        _key = Key.make("CompressedDT" + Key.rand());
        _nodes = null;
        _nodesObj = nodes;
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
        boolean isALeaf = _nodesObj[actualNodeIndex] instanceof CompressedLeaf;
        // first value 1 means that the node is list, return prediction for the list
        if (isALeaf) {
            double decisionValue = ((CompressedLeaf) _nodesObj[actualNodeIndex]).getDecisionValue();
            double probability = ((CompressedLeaf) _nodesObj[actualNodeIndex]).getProbabilities();
            return new DTPrediction((int) decisionValue, probability, ruleExplanation + " -> (" 
                    + decisionValue + ", probabilities: " + probability + ", " + (1 - probability) + ")");
        }
        if (!ruleExplanation.isEmpty()) {
            ruleExplanation += " and ";
        }
        AbstractSplittingRule splittingRule = ((CompressedNode) _nodesObj[actualNodeIndex]).getSplittingRule();
        // splitting rule is true - left, false - right
        if(splittingRule.routeSample(rowValues)) {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 1, 
                    ruleExplanation + splittingRule.toString());
        } else {
            return predictRowStartingFromNode(rowValues, 2 * actualNodeIndex + 2, 
                    ruleExplanation + "not " + splittingRule.toString());
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(_nodes).map(n -> "(" + n[0] + "," + n[1] + "," + n[2] + ")").collect(Collectors.joining(";"));
    }

    public void extractRulesStartingWithNode(int nodeIndex, String actualRule) {
        if(_nodes != null) {
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
        } else {
            if (_nodesObj[nodeIndex] instanceof CompressedLeaf) {
                // if node is a list, add the rule to the list and return
                _listOfRules.add(actualRule + " -> (" + ((CompressedLeaf) _nodesObj[nodeIndex]).getDecisionValue()
                        + ", " + ((CompressedLeaf) _nodesObj[nodeIndex]).getProbabilities() + ")");
                return;
            }

            actualRule = actualRule.isEmpty() ? actualRule : actualRule + " and ";
            // proceed to the left branch
            extractRulesStartingWithNode(2 * nodeIndex + 1, actualRule +
                    "(x" +  ((NumericSplittingRule)((CompressedNode) _nodesObj[nodeIndex]).getSplittingRule()).getField()
                    + " <= " + ((NumericSplittingRule)((CompressedNode) _nodesObj[nodeIndex]).getSplittingRule()).getThreshold() + ")");
            // proceed to the right branch
            extractRulesStartingWithNode(2 * nodeIndex + 2, actualRule +
                    "(x" + ((NumericSplittingRule)((CompressedNode) _nodesObj[nodeIndex]).getSplittingRule()).getField()
                    + " > " + ((NumericSplittingRule)((CompressedNode) _nodesObj[nodeIndex]).getSplittingRule()).getThreshold() + ")");
        }
    }

    public List<String> getListOfRules() {
        return _listOfRules;
    }

    public double[][] getNodes() {
        return _nodes;
    }

}
