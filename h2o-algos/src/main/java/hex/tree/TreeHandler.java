package hex.tree;

import hex.ModelCategory;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.schemas.TreeV3;
import water.Keyed;
import water.MemoryManager;
import water.api.Handler;

import java.util.*;

/**
 * Handling requests for various model trees
 */
public class TreeHandler extends Handler {
    private static final int NO_CHILD = -1;

    public TreeV3 getTree(final int version, final TreeV3 args) {

        final Keyed possibleModel = args.model.key().get();
        if (possibleModel == null) throw new IllegalArgumentException("Given model does not exist: " + args.model.key().toString());
        if(! (possibleModel instanceof SharedTreeModel)){
            throw new IllegalArgumentException("Given model is not tree-based.");
        }

        final SharedTreeModel model = (SharedTreeModel) possibleModel;
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
        args.levels = treeProperties.levels;
        args.predictions = treeProperties._predictions;
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
        final String trimmedCategorical = categorical != null ? categorical.trim() : ""; // Trim the categorical once - input from the user

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
        treeprops._predictions = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());

        // Set root node's children, there is no guarantee the root node will be number 0
        treeprops._rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        treeprops._leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;
        treeprops._thresholds[0] = sharedTreeSubgraph.rootNode.getSplitValue();
        treeprops._features[0] = sharedTreeSubgraph.rootNode.getColName();
        treeprops._nas[0] = getNaDirection(sharedTreeSubgraph.rootNode);
        treeprops.levels = new int[sharedTreeSubgraph.nodesArray.size()][];

        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(treeprops._rightChildren, treeprops._leftChildren,
                treeprops._descriptions, treeprops._thresholds, treeprops._features, treeprops._nas,
                treeprops.levels, treeprops._predictions, nodesToTraverse, -1, false);

        return treeprops;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, final String[] nodesDescriptions,
                               final float[] thresholds, final String[] splitColumns, final String[] naHandlings,
                               final int[][] levels, final float[] predictions,
                               final List<SharedTreeNode> nodesToTraverse, int pointer, boolean visitedRoot) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> discoveredNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            if(visitedRoot){
                fillnodeDescriptions(node, nodesDescriptions, thresholds, splitColumns, levels, predictions,
                        naHandlings, pointer);
            } else {
                StringBuilder rootDescriptionBuilder = new StringBuilder();
                rootDescriptionBuilder.append("Root node has id ");
                rootDescriptionBuilder.append(node.getNodeNumber());
                rootDescriptionBuilder.append(". ");
                fillNodeSplitTowardsChildren(rootDescriptionBuilder, node);
                nodesDescriptions[pointer] = rootDescriptionBuilder.toString();
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

        append(rightChildren, leftChildren, nodesDescriptions, thresholds, splitColumns, naHandlings, levels, predictions,
                discoveredNodes, pointer, true);
    }

    private static void fillnodeDescriptions(final SharedTreeNode node, final String[] nodeDescriptions,
                                             final float[] thresholds, final String[] splitColumns, final int[][] levels,
                                             final float[] predictions, final String[] naHandlings, final int pointer) {
        final StringBuilder nodeDescriptionBuilder = new StringBuilder();
        int[] nodeLevels = node.getParent().isBitset() ? extractNodeLevels(node) : null;
        nodeDescriptionBuilder.append("Node has id ");
        nodeDescriptionBuilder.append(node.getNodeNumber());
        if (node.getColName() != null) {
            nodeDescriptionBuilder.append(" and splits on column [");
            nodeDescriptionBuilder.append(node.getColName());
            nodeDescriptionBuilder.append("]. ");
        } else {
            nodeDescriptionBuilder.append(" and is a terminal node. ");
        }

        fillNodeSplitTowardsChildren(nodeDescriptionBuilder, node);

        if (!Float.isNaN(node.getParent().getSplitValue())) {
            nodeDescriptionBuilder.append(" Parent node split threshold is ");
            nodeDescriptionBuilder.append(node.getParent().getSplitValue());
            nodeDescriptionBuilder.append(".");
        } else if (node.getParent().isBitset()) {
            nodeLevels = extractNodeLevels(node);
            assert nodeLevels != null;
            nodeDescriptionBuilder.append(" Parent node split on column [");
            nodeDescriptionBuilder.append(node.getParent().getColName());
            nodeDescriptionBuilder.append("]. Inherited categorical levels from parent split: ");
            for (int nodeLevelsindex = 0; nodeLevelsindex < nodeLevels.length; nodeLevelsindex++) {
                nodeDescriptionBuilder.append(node.getParent().getDomainValues()[nodeLevels[nodeLevelsindex]]);
                if (nodeLevelsindex != nodeLevels.length - 1) nodeDescriptionBuilder.append(",");
            }
        } else {
            nodeDescriptionBuilder.append("NA only");
        }

        nodeDescriptions[pointer] = nodeDescriptionBuilder.toString();
        splitColumns[pointer] = node.getColName();
        naHandlings[pointer] = getNaDirection(node);
        levels[pointer] = nodeLevels;
        predictions[pointer] = node.getPredValue();
        thresholds[pointer] = node.getSplitValue();
    }

    private static void fillNodeSplitTowardsChildren(final StringBuilder nodeDescriptionBuilder, final SharedTreeNode node){
        if (!Float.isNaN(node.getSplitValue())) {
            nodeDescriptionBuilder.append("Split threshold is ");
            if (node.getLeftChild() != null) {
                nodeDescriptionBuilder.append(" < ");
                nodeDescriptionBuilder.append(node.getSplitValue());
                nodeDescriptionBuilder.append(" to the left node (");
                nodeDescriptionBuilder.append(node.getLeftChild().getNodeNumber());
                nodeDescriptionBuilder.append(")");
            }

            if (node.getLeftChild() != null) {
                if(node.getLeftChild() != null) nodeDescriptionBuilder.append(", ");
                nodeDescriptionBuilder.append(" >= ");
                nodeDescriptionBuilder.append(node.getSplitValue());
                nodeDescriptionBuilder.append(" to the right node (");
                nodeDescriptionBuilder.append(node.getRightChild().getNodeNumber());
                nodeDescriptionBuilder.append(")");
            }
            nodeDescriptionBuilder.append(".");
        } else if (node.isBitset()) {
            fillNodeCategoricalSplitDescription(nodeDescriptionBuilder, node);
        }
    }

    private static int[] extractNodeLevels(final SharedTreeNode node) {
        final BitSet childInclusiveLevels = node.getInclusiveLevels();
        final int cardinality = childInclusiveLevels.cardinality();
        if (cardinality > 0) {
            int[] nodeLevels = MemoryManager.malloc4(cardinality);
            int bitsignCounter = 0;
            for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i + 1)) {
                nodeLevels[bitsignCounter] = i;
                bitsignCounter++;
            }
            return nodeLevels;
        }
        return null;
    }

    private static void fillNodeCategoricalSplitDescription(final StringBuilder nodeDescriptionBuilder, final SharedTreeNode node) {
        final SharedTreeNode leftChild = node.getLeftChild();
        final SharedTreeNode rightChild = node.getRightChild();
        final int[] leftChildLevels = extractNodeLevels(leftChild);
        final int[] rightChildLevels = extractNodeLevels(rightChild);

        if (leftChild != null) {
            nodeDescriptionBuilder.append(" Left child node (");
            nodeDescriptionBuilder.append(leftChild.getNodeNumber());
            nodeDescriptionBuilder.append(") inherits categorical levels: ");

            for (int nodeLevelsindex = 0; nodeLevelsindex < leftChildLevels.length; nodeLevelsindex++) {
                nodeDescriptionBuilder.append(node.getDomainValues()[leftChildLevels[nodeLevelsindex]]);
                if (nodeLevelsindex != leftChildLevels.length - 1) nodeDescriptionBuilder.append(",");
            }
        }

        if (rightChild != null) {
            nodeDescriptionBuilder.append(". Right child node (");
            nodeDescriptionBuilder.append(rightChild.getNodeNumber());
            nodeDescriptionBuilder.append(") inherits categorical levels: ");

            for (int nodeLevelsindex = 0; nodeLevelsindex < rightChildLevels.length; nodeLevelsindex++) {
                nodeDescriptionBuilder.append(node.getDomainValues()[rightChildLevels[nodeLevelsindex]]);
                if (nodeLevelsindex != rightChildLevels.length - 1) nodeDescriptionBuilder.append(",");
            }
        }
        nodeDescriptionBuilder.append(". ");


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
        public int[][] levels; // Categorical levels, points to a list of categoricals that is already existing within the model on the client.
        public String[] _nas;
        public float[] _predictions; // Prediction values on terminal nodes

    }
}
