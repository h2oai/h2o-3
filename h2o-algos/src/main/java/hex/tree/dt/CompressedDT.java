package hex.tree.dt;

import water.Key;
import water.Keyed;

import java.util.Arrays;
import java.util.stream.Collectors;


/**
 * Compressed DT class containing tree as array.
 */
public class CompressedDT extends Keyed<CompressedDT> {

    /**
     * List of nodes, for each node holds either split feature index and threshold or just decision value if it is list.
     */
    private final AbstractCompressedNode[] _nodes;

    private final String[] _listOfRules;

    public CompressedDT(AbstractCompressedNode[] nodes, int leavesCount) {
        _key = Key.make("CompressedDT" + Key.rand());
        _nodes = nodes;
        _listOfRules = new String[leavesCount];
        extractRulesStartingWithNode(0, "", 0);
    }

    /**
     * Makes prediction by recursively evaluating the data through the tree.
     *
     * @param rowValues       - data row to find prediction for
     * @param actualNodeIndex - actual node to evaluate and then go to selected child
     * @return class label
     */
    public DTPrediction predictRowStartingFromNode(final double[] rowValues, final int actualNodeIndex, String ruleExplanation) {
        boolean isALeaf = _nodes[actualNodeIndex] instanceof CompressedLeaf;
        // first value 1 means that the node is a leaf, return prediction for the leaf
        if (isALeaf) {
            double decisionValue = ((CompressedLeaf) _nodes[actualNodeIndex]).getDecisionValue();
            double[] probabilities = ((CompressedLeaf) _nodes[actualNodeIndex]).getProbabilities();
            return new DTPrediction((int) decisionValue, probabilities, 
                    ruleExplanation + " -> " + _nodes[actualNodeIndex].toString());
        }
        if (!ruleExplanation.isEmpty()) {
            ruleExplanation += " and ";
        }
        AbstractSplittingRule splittingRule = ((CompressedNode) _nodes[actualNodeIndex]).getSplittingRule();
        // splitting rule is: true - left, false - right
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
        return Arrays.stream(_nodes).map(AbstractCompressedNode::toString).collect(Collectors.joining(";"));
    }

    public int extractRulesStartingWithNode(int nodeIndex, String actualRule, int nextFreeSpot) {
        if (_nodes[nodeIndex] instanceof CompressedLeaf) {
            // if node is a leaf, add the rule to the list of rules at index given by the nextFreeSpot parameter
            _listOfRules[nextFreeSpot] = actualRule + " -> (" + ((CompressedLeaf) _nodes[nodeIndex]).getDecisionValue()
                    + ", " + Arrays.toString(((CompressedLeaf) _nodes[nodeIndex]).getProbabilities()) + ")";
            // move nextFreeSpot to the next index and return it to be used for other branches
            nextFreeSpot++;
            return nextFreeSpot;
        }

        actualRule = actualRule.isEmpty() ? actualRule : actualRule + " and ";
        // proceed to the left branch
        nextFreeSpot = extractRulesStartingWithNode(2 * nodeIndex + 1, 
                actualRule + ((CompressedNode) _nodes[nodeIndex]).getSplittingRule().toString(), nextFreeSpot);
        // proceed to the right branch
        nextFreeSpot = extractRulesStartingWithNode(2 * nodeIndex + 2, 
                actualRule + " not (" + ((CompressedNode) _nodes[nodeIndex]).getSplittingRule().toString() + ")", 
                nextFreeSpot);
        // return current index of the next free spot in the array
        return nextFreeSpot;
    }

    public String[] getListOfRules() {
        return _listOfRules;
    }

    public AbstractCompressedNode[] getNodes() {
        return _nodes;
    }

}
