package hex.tree;

import hex.ModelCategory;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.schemas.TreeV3;
import water.MemoryManager;
import water.api.Handler;

import java.util.*;

/**
 * Handling requests for various model trees
 */
public class TreeHandler extends Handler {
    private static final int NO_CHILD = -1;

    public TreeV3 getTree(final int version, final TreeV3 args) {
        final SharedTreeModel model = (SharedTreeModel) args.model.key().get();
        if (model == null) throw new IllegalArgumentException("Given model does not exist: " + args.model.key().toString());
        final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
        final int treeClass = getResponseLevelIndex(args.tree_class, sharedTreeOutput);
        validateArgs(args, sharedTreeOutput, treeClass);

        final SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(args.tree_number, treeClass);
        final TreeProperties treeProperties = convertSharedTreeSubgraph(sharedTreeSubgraph);

        args.left_children = treeProperties._leftChildren;
        args.right_children = treeProperties._rightChildren;
        args.descriptions = treeProperties._descriptions;
        args.root_node_id = sharedTreeSubgraph.rootNode.getNodeNumber();
        args.thresholds = treeProperties._thresholds;
        args.features = treeProperties._features;
        args.nas = treeProperties._nas;
        // Class may not be provided by the user, should be always filled correctly on output. NULL for regression.
        if (ModelCategory.Regression.equals(sharedTreeOutput.getModelCategory())) {
            args.tree_class = null;
        } else {
            args.tree_class = sharedTreeOutput._domains[sharedTreeOutput.responseIdx()][treeClass];
        }

        return args;
    }

    private static int getResponseLevelIndex(final String categorical, final SharedTreeModel.SharedTreeOutput sharedTreeOutput) {
        final String[] responseColumnDomain = sharedTreeOutput._domains[sharedTreeOutput.responseIdx()];
        final String trimmedCategorical = categorical.trim(); // Trim the categorical once - input from the user

        switch (sharedTreeOutput.getModelCategory()) {
            case Binomial:
                if (!trimmedCategorical.isEmpty() && !trimmedCategorical.equals(responseColumnDomain[0])) {
                    throw new IllegalArgumentException("For binomial, only one tree class has been built per each iteration: " + responseColumnDomain[0]);
                } else {
                    return 0;
                }
            case Regression:
                if (!trimmedCategorical.isEmpty())
                    throw new IllegalArgumentException("There are no tree classes for regression.");
                return 0; // There is only one tree for regression
            default:
                for (int i = 0; i < responseColumnDomain.length; i++) {
                    // User is supposed to enter the name of the categorical level correctly, not ignoring case
                    if (trimmedCategorical.equals(responseColumnDomain[i])) return i;
                }
        }
        return -1;
    }

    /**
     * @param args An instance of {@link TreeV3} input arguments to validate
     * @param output An instance of {@link SharedTreeModel.SharedTreeOutput} to validate input arguments against
     * @param responseLevelIndex
     */
    private static void validateArgs(TreeV3 args, SharedTreeModel.SharedTreeOutput output, final int responseLevelIndex) {
        if (args.tree_number < 0) throw new IllegalArgumentException("Tree number must be greater than 0.");

        if (args.tree_number > output._treeKeys.length - 1)
            throw new IllegalArgumentException("There is no such tree.");

        if (responseLevelIndex < 0)
            throw new IllegalArgumentException("There is no such tree class. Given categorical level does not exist in response column: " + args.tree_class.trim());
    }


    /**
     * Converts H2O-3's internal representation of a boosted tree in a form of {@link SharedTreeSubgraph} to a format
     * expected by H2O clients.
     *
     * @param sharedTreeSubgraph An instance of {@link SharedTreeSubgraph} to convert
     * @return An instance of {@link TreeProperties} with some attributes possibly empty if suitable. Never null.
     */
    static TreeProperties convertSharedTreeSubgraph(final SharedTreeSubgraph sharedTreeSubgraph) {
        Objects.requireNonNull(sharedTreeSubgraph);

        final TreeProperties treeprops = new TreeProperties();

        treeprops._leftChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._rightChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._descriptions = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._thresholds = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());
        treeprops._features = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._nas = new String[sharedTreeSubgraph.nodesArray.size()];

        // Set root node's children, there is no guarantee the root node will be number 0
        treeprops._rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        treeprops._leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;
        treeprops._thresholds[0] = sharedTreeSubgraph.rootNode.getSplitValue();
        treeprops._features[0] = sharedTreeSubgraph.rootNode.getColName();
        treeprops._nas[0] = getNaDirection(sharedTreeSubgraph.rootNode);

        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(treeprops._rightChildren, treeprops._leftChildren,
                treeprops._descriptions, treeprops._thresholds, treeprops._features, treeprops._nas,
                nodesToTraverse, -1, false);

        return treeprops;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, final String[] nodesDescriptions,
                               final float[] thresholds, final String[] splitColumns, final String[] naHandlings,
                               List<SharedTreeNode> nodesToTraverse, int pointer, boolean visitedRoot) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> discoveredNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            if(visitedRoot){
                fillnodeDescriptions(node, nodesDescriptions, thresholds, splitColumns, naHandlings, pointer);
            } else {
                nodesDescriptions[pointer] = "Root node";
                visitedRoot = true;
            }

            if (leftChild != null) {
                discoveredNodes.add(leftChild);
                leftChildren[pointer] = leftChild.getNodeNumber();
            } else {
                leftChildren[pointer] = NO_CHILD;
            }

            if (rightChild != null) {
                discoveredNodes.add(rightChild);
                rightChildren[pointer] = rightChild.getNodeNumber();
            } else {
                rightChildren[pointer] = NO_CHILD;
            }
        }

        append(rightChildren, leftChildren, nodesDescriptions, thresholds, splitColumns, naHandlings,
                discoveredNodes, pointer, true);
    }

    private static void fillnodeDescriptions(final SharedTreeNode node, final String[] nodeDescriptions,
                                             final float[] thresholds, final String[] splitColumns,
                                             final String[] naHandlings, final int pointer) {
        final StringBuilder nodeDescriptionBuilder = new StringBuilder();

        if (!Float.isNaN(node.getParent().getSplitValue())) {
            nodeDescriptionBuilder.append("Parent split threshold: ");
            nodeDescriptionBuilder.append(node.getParent().getColName());
            if (node.isLeftward()) {
                nodeDescriptionBuilder.append(" < ");
            } else {
                nodeDescriptionBuilder.append(" >= ");
            }
            nodeDescriptionBuilder.append(node.getParent().getSplitValue());
        } else if (node.getParent().isBitset()) {
            final BitSet childInclusiveLevels = node.getInclusiveLevels();
            final int cardinality = childInclusiveLevels.cardinality();
            if ((cardinality > 0)) {
                nodeDescriptionBuilder.append("Parent split column [");
                nodeDescriptionBuilder.append(node.getParent().getColName());
                nodeDescriptionBuilder.append("]: ");
                int bitsignCounter = 0;
                for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i + 1)) {
                    nodeDescriptionBuilder.append(node.getParent().getDomainValues()[i]);
                    if (bitsignCounter != cardinality - 1) nodeDescriptionBuilder.append(", ");
                    bitsignCounter++;
                }
            }
        } else{
            nodeDescriptionBuilder.append("NA only");
        }

        nodeDescriptions[pointer] = nodeDescriptionBuilder.toString();
        splitColumns[pointer] = node.getColName();
        naHandlings[pointer] = getNaDirection(node);
        thresholds[pointer] = node.getSplitValue();


    }

    private static String getNaDirection(final SharedTreeNode node) {
        final boolean leftNa = node.getLeftChild() != null && node.getLeftChild().isInclusiveNa();
        final boolean rightNa = node.getRightChild() != null && node.getRightChild().isInclusiveNa();
        assert (rightNa ^ leftNa) || (rightNa == false && leftNa == false);

        if (leftNa) {
            return "LEFT";
        } else if (rightNa) {
            return "RIGHT";
        }
        return null; // No direction
    }

    public static class TreeProperties {

        public int[] _leftChildren;
        public int[] _rightChildren;
        public String[] _descriptions; // General node description, most likely to contain serialized threshold or inclusive dom. levels
        public float[] _thresholds;
        public String[] _features;
        public String[] _nas;

    }
}
