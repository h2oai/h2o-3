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

        final CompressedTreeFormat compressedTreeFormat = convertSharedTreeSubgraph(sharedTreeSubgraph);

        args.left_children = compressedTreeFormat.leftChildren;
        args.right_children = compressedTreeFormat.rightChildren;
        args.root_node_number = compressedTreeFormat.rootNodeNumber;
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
     * @return An instance of {@link CompressedTreeFormat} with some attributes possibly empty if suitable. Never null.
     */
    static CompressedTreeFormat convertSharedTreeSubgraph(final SharedTreeSubgraph sharedTreeSubgraph) {
        Objects.requireNonNull(sharedTreeSubgraph);

        final CompressedTreeFormat stf = new CompressedTreeFormat();
        stf.rootNodeNumber = sharedTreeSubgraph.rootNode.getNodeNumber();

        stf.leftChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        stf.rightChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        stf.thresholds = MemoryManager.malloc8d(sharedTreeSubgraph.nodesArray.size());

        // Create a temporary nodeMap for faster search. Our nodes are not always numbered sequentially,
        // thus searching in the array of nodes by index is not possible.
        final Map<Integer, SharedTreeNode> nodeMap = createTreeNodeMap(sharedTreeSubgraph.nodesArray);

        // Set root node's children, there is no guarantee the root node will be number 0
        stf.rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        stf.leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;

        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(stf.rightChildren, stf.leftChildren, nodesToTraverse, -1);

        return stf;
    }

    /**
     * Creates a map of {@link SharedTreeNode} where number of the node is the key.
     *
     * @param sharedTreeNodes A {@link List} of {@link SharedTreeNode} to create map from
     * @return An non-thread-safe instance of {@link Map} with node number being the key to a {@link SharedTreeNode}
     */
    private static Map<Integer, SharedTreeNode> createTreeNodeMap(List<SharedTreeNode> sharedTreeNodes) {
        Map<Integer, SharedTreeNode> treeNodeMap = new HashMap<>(sharedTreeNodes.size());

        for (SharedTreeNode sharedTreeNode : sharedTreeNodes) {
            treeNodeMap.put(sharedTreeNode.getNodeNumber(), sharedTreeNode);
        }

        assert treeNodeMap.size() == sharedTreeNodes.size(); // Verify there are no duplicated tree nodes (nodes with the same node number)

        return treeNodeMap;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, List<SharedTreeNode> nodesToTraverse, int pointer) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> newNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            if (leftChild != null) {
                newNodes.add(leftChild);
                leftChildren[pointer] = leftChild.getNodeNumber();
            } else {
                leftChildren[pointer] = NO_CHILD;
            }

            if (rightChild != null) {
                newNodes.add(rightChild);
                rightChildren[pointer] = rightChild.getNodeNumber();
            } else {
                rightChildren[pointer] = NO_CHILD;
            }

        }

        append(rightChildren, leftChildren, newNodes, pointer);

    }

    public static class CompressedTreeFormat {

        private int[] leftChildren;
        private int[] rightChildren;
        private double[] thresholds;
        private String[] features;
        private int rootNodeNumber;

        public int[] getLeftChildren() {
            return leftChildren;
        }

        public int[] getRightChildren() {
            return rightChildren;
        }

        public double[] getThresholds() {
            return thresholds;
        }

        public String[] getFeatures() {
            return features;
        }

        public int getRootNodeNumber() {
            return rootNodeNumber;
        }
    }
}
