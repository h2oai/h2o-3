package hex.tree;

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
        validateArgs(args);
        final SharedTreeModel model = (SharedTreeModel) args.model.key().get();
        if (model == null) throw new IllegalArgumentException("Unknown tree key: " + args.model.toString());

        final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
        final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[args.tree_number][args.tree_class].get();
        final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[args.tree_number][args.tree_class].get()
                .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

        final TreeProperties treeProperties = convertSharedTreeSubgraph(sharedTreeSubgraph);

        args.left_children = treeProperties.leftChildren;
        args.right_children = treeProperties.rightChildren;
        args.root_node_number = treeProperties.rootNodeNumber;
        return args;
    }


    /**
     * @param args An instance of {@link TreeV3} input arguments to validate
     */
    private void validateArgs(TreeV3 args) {
        if (args.tree_number < 0) throw new IllegalArgumentException("Tree number must be greater than 0.");
        if (args.tree_class < 0) throw new IllegalArgumentException("Tree class must be greater than 0.");
    }


    /**
     * Convers H2O-3's internal representation of a boosted tree in a form of {@link SharedTreeSubgraph} to a compressed format.
     *
     * @param sharedTreeSubgraph An instance of {@link SharedTreeSubgraph} to convert
     * @return An instance of {@link TreeProperties} with some attributes possibly empty if suitable. Never null.
     */
    static TreeProperties convertSharedTreeSubgraph(final SharedTreeSubgraph sharedTreeSubgraph) {
        Objects.requireNonNull(sharedTreeSubgraph);

        final TreeProperties stf = new TreeProperties();
        stf.rootNodeNumber = sharedTreeSubgraph.rootNode.getNodeNumber();

        stf.leftChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        stf.rightChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        stf.descriptions = new String[sharedTreeSubgraph.nodesArray.size()];

        // Set root node's children, there is no guarantee the root node will be number 0
        stf.rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        stf.leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;

        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(stf.rightChildren, stf.leftChildren,
                stf.descriptions, nodesToTraverse, -1);

        return stf;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, final String[] nodesDescriptions,
                               List<SharedTreeNode> nodesToTraverse, int pointer) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> discoveredNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            nodesDescriptions[pointer] = serializeNodeDescription(node);

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

        append(rightChildren, leftChildren, nodesDescriptions, discoveredNodes, pointer);
    }

    private static String serializeNodeDescription(final SharedTreeNode node) {
        final StringBuffer nodeDescriptionBuffer = new StringBuffer();

        if (!Float.isNaN(node.getSplitValue())) {
            if (node.isLeftward()) {
                nodeDescriptionBuffer.append("Split < ");
            } else {
                nodeDescriptionBuffer.append("Split >= ");
            }
            nodeDescriptionBuffer.append(node.getSplitValue());
        }


        // Append inclusive levels, if there are any. Otherwise return the node description immediately.
        if (!node.isBitset() || node.getInclusiveLevels() == null) return nodeDescriptionBuffer.toString();

        final BitSet childInclusiveLevels = node.getInclusiveLevels();
        final int cardinality = childInclusiveLevels.cardinality();
        if ((cardinality > 0)) {
            nodeDescriptionBuffer.append(" | Inclusive levels: ");
            for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i + 1)) {
                nodeDescriptionBuffer.append(node.getDomainValues()[i]);
                nodeDescriptionBuffer.append(", ");
            }
        }

        return nodeDescriptionBuffer.toString();

    }

    public static class TreeProperties {

        private int[] leftChildren;
        private int[] rightChildren;
        private String[] descriptions; // General node description, most likely to contain serialized threshold or inclusive dom. levels
        private int rootNodeNumber;

        public int[] getLeftChildren() {
            return leftChildren;
        }

        public int[] getRightChildren() {
            return rightChildren;
        }

        public int getRootNodeNumber() {
            return rootNodeNumber;
        }

        public String[] getDescriptions() {
            return descriptions;
        }
    }
}
