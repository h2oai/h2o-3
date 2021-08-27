package hex.tree;

import hex.Model;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;
import hex.schemas.TreeV3;
import water.Keyed;
import water.MemoryManager;
import water.api.Handler;
import java.util.*;
import java.util.stream.IntStream;

import static hex.tree.TreeUtils.getResponseLevelIndex;

/**
 * Handling requests for various model trees
 */
public class TreeHandler extends Handler {
    private static final int NO_CHILD = -1;

    public enum PlainLanguageRules {AUTO, TRUE, FALSE}

    public TreeV3 getTree(final int version, final TreeV3 args) {

        if (args.tree_number < 0) {
            throw new IllegalArgumentException("Invalid tree number: " + args.tree_number + ". Tree number must be >= 0.");
        }

        final Keyed possibleModel = args.model.key().get();
        if (possibleModel == null) throw new IllegalArgumentException("Given model does not exist: " + args.model.key().toString());

        else if (!(possibleModel instanceof SharedTreeModel) && !(possibleModel instanceof SharedTreeGraphConverter)) {
            throw new IllegalArgumentException("Given model is not tree-based.");
        }
        final SharedTreeSubgraph sharedTreeSubgraph;

        if (possibleModel instanceof SharedTreeGraphConverter) {
            final SharedTreeGraphConverter treeBackedModel = (SharedTreeGraphConverter) possibleModel;
            final SharedTreeGraph sharedTreeGraph = treeBackedModel.convert(args.tree_number, args.tree_class);
            assert sharedTreeGraph.subgraphArray.size() == 1;
            sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);

            if (! ((Model)possibleModel)._output.isClassifier()) {
                args.tree_class = null; // Class may not be provided by the user, should be always filled correctly on output. NULL for regression.
            }
        } else {
            final SharedTreeModel model = (SharedTreeModel) possibleModel;
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
            final int treeClass = getResponseLevelIndex(args.tree_class, sharedTreeOutput);
            sharedTreeSubgraph = model.getSharedTreeSubgraph(args.tree_number, treeClass);
            // Class may not be provided by the user, should be always filled correctly on output. NULL for regression.
            args.tree_class = sharedTreeOutput.isClassifier() ? sharedTreeOutput.classNames()[treeClass] : null;
        }


        final TreeProperties treeProperties = convertSharedTreeSubgraph(sharedTreeSubgraph, args.plain_language_rules);

        args.left_children = treeProperties._leftChildren;
        args.right_children = treeProperties._rightChildren;
        args.descriptions = treeProperties._descriptions;
        args.root_node_id = sharedTreeSubgraph.rootNode.getNodeNumber();
        args.thresholds = treeProperties._thresholds;
        args.features = treeProperties._features;
        args.nas = treeProperties._nas;
        args.levels = treeProperties.levels;
        args.predictions = treeProperties._predictions;
        args.tree_decision_path = treeProperties._treeDecisionPath;
        args.decision_paths = treeProperties._decisionPaths;
        return args;
    }


    private static String getLanguageRepresentation(SharedTreeSubgraph sharedTreeSubgraph) {
        return getNodeRepresentation(sharedTreeSubgraph.rootNode, new StringBuilder(), 0).toString();
    }

    private static StringBuilder getNodeRepresentation(SharedTreeNode node, StringBuilder languageRepresentation, int padding) {
        if (node.getRightChild() != null) {
            languageRepresentation.append(getConditionLine(node, padding));
            languageRepresentation.append(getNewPaddedLine(padding));
            languageRepresentation = getNodeRepresentation(node.getRightChild(), languageRepresentation, padding +1);
            languageRepresentation.append(getNewPaddedLine(padding));
            languageRepresentation.append(getElseLine(node));
            languageRepresentation.append(getNewPaddedLine(padding));
            languageRepresentation = getNodeRepresentation(node.getLeftChild(), languageRepresentation, padding + 1);
            languageRepresentation.append(getNewPaddedLine(padding));
            languageRepresentation.append("}");
        } else {
            languageRepresentation.append(getNewPaddedLine(padding));
            if (Float.compare(node.getPredValue(),Float.NaN) != 0) {
                languageRepresentation.append("Predicted value: " + node.getPredValue());
            } else {
                languageRepresentation.append("Predicted value: NaN");
            }
            languageRepresentation.append(getNewPaddedLine(padding));
        }
        return languageRepresentation;
    }

    private static StringBuilder getNewPaddedLine(int padding) {
        StringBuilder line = new StringBuilder("\n");
        for(int i = 0; i < padding; i++) {
            line.append("\t");
        }
        return line;
    }

    private static StringBuilder getElseLine(SharedTreeNode node) {
        StringBuilder elseLine = new StringBuilder();
        if (node.getDomainValues() == null) {
            elseLine.append("} else {");
        } else {
            SharedTreeNode leftChild = node.getLeftChild();
            elseLine.append("} else if ( ").append(node.getColName()).append(" is in [ ");
            BitSet inclusiveLevelsSet = leftChild.getInclusiveLevels();
            if (inclusiveLevelsSet != null) {
                String stringToParseInclusiveLevelsFrom = inclusiveLevelsSet.toString();
                int inclusiveLevelsLength = inclusiveLevelsSet.toString().length();
                if (inclusiveLevelsLength  > 2) {
                    // get rid of curly braces:
                    stringToParseInclusiveLevelsFrom = stringToParseInclusiveLevelsFrom.substring(1, inclusiveLevelsLength - 1);
                    String[] inclusiveLevels = stringToParseInclusiveLevelsFrom.split(",");
                    for (String index : inclusiveLevels) {
                        elseLine.append(node.getDomainValues()[Integer.parseInt(index.trim())] + " ");
                    }
                } else {
                    elseLine.append("Missing set of levels for underlying node");
                }
            } 
            elseLine.append("]) {");
        }
        return elseLine;
    }

    private static StringBuilder getConditionLine(SharedTreeNode node, int padding) {
        StringBuilder conditionLine = new StringBuilder();
        if (padding != 0) {
            conditionLine.append(getNewPaddedLine(padding)); 
        }
        if (node.getDomainValues() == null) {
            if (Float.compare(node.getSplitValue(),Float.NaN) == 0) {
                conditionLine.append("If ( " + node.getColName() + " is NaN ) {");
            } else {
                conditionLine.append("If ( " + node.getColName() + " >= " + node.getSplitValue());
                if ("RIGHT".equals(getNaDirection(node))) {
                    conditionLine.append(" or ").append(node.getColName()).append(" is NaN ) {");
                } else {
                    conditionLine.append(" ) {");
                }
            }
        } else {
            conditionLine.append("If ( " + node.getColName() + " is in [ ");
            // get inclusive levels:
            SharedTreeNode rightChild = node.getRightChild();
            String stringToParseInclusiveLevelsFrom = rightChild.getInclusiveLevels().toString();
            int inclusiveLevelsLength = rightChild.getInclusiveLevels().toString().length();
            if (inclusiveLevelsLength  > 2) {
                // get rid of curly braces:
                stringToParseInclusiveLevelsFrom = stringToParseInclusiveLevelsFrom.substring(1, inclusiveLevelsLength - 1);
                String[] inclusiveLevels = stringToParseInclusiveLevelsFrom.split(",");
                Arrays.stream(inclusiveLevels)
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .forEach(index -> conditionLine.append(node.getDomainValues()[index] + " "));
            } else {
                conditionLine.append("Missing set of levels for underlying node");
            }
            conditionLine.append("]) {");
        }
        return conditionLine;
    }

    /**
     * Converts H2O-3's internal representation of a tree in a form of {@link SharedTreeSubgraph} to a format
     * expected by H2O clients.
     *
     * @param sharedTreeSubgraph An instance of {@link SharedTreeSubgraph} to convert
     * @return An instance of {@link TreeProperties} with some attributes possibly empty if suitable. Never null.
     */
    static TreeProperties convertSharedTreeSubgraph(final SharedTreeSubgraph sharedTreeSubgraph, PlainLanguageRules plainLanguageRules) {
        Objects.requireNonNull(sharedTreeSubgraph);

        final TreeProperties treeprops = new TreeProperties();

        treeprops._leftChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._rightChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._descriptions = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._thresholds = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());
        treeprops._features = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._nas = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._predictions = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());
        treeprops._leafNodeAssignments = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._decisionPaths = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._leftChildrenNormalized = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._rightChildrenNormalized = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());

        // Set root node's children, there is no guarantee the root node will be number 0
        treeprops._rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        treeprops._leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;
        treeprops._thresholds[0] = sharedTreeSubgraph.rootNode.getSplitValue();
        treeprops._features[0] = sharedTreeSubgraph.rootNode.getColName();
        treeprops._nas[0] = getNaDirection(sharedTreeSubgraph.rootNode);
        treeprops.levels = new int[sharedTreeSubgraph.nodesArray.size()][];
        if (plainLanguageRules.equals(PlainLanguageRules.AUTO)) {
            /* 255 = number of nodes for complete binary tree of depth 7 (2^(k+1)−1) */
            plainLanguageRules = sharedTreeSubgraph.nodesArray.size() < 256 ? PlainLanguageRules.TRUE : PlainLanguageRules.FALSE;
        }
        if (plainLanguageRules.equals(PlainLanguageRules.TRUE)) { 
            treeprops._treeDecisionPath = getLanguageRepresentation(sharedTreeSubgraph);
            treeprops._decisionPaths[0] = "Predicted value: " + sharedTreeSubgraph.rootNode.getPredValue();
            treeprops._leftChildrenNormalized[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;
            treeprops._rightChildrenNormalized[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        }
        treeprops._domainValues = new String[sharedTreeSubgraph.nodesArray.size()][];
        treeprops._domainValues[0] = sharedTreeSubgraph.rootNode.getDomainValues();

        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(treeprops._rightChildren, treeprops._leftChildren,
                treeprops._descriptions, treeprops._thresholds, treeprops._features, treeprops._nas,
                treeprops.levels, treeprops._predictions, nodesToTraverse, -1, false, treeprops._domainValues);
        if (plainLanguageRules.equals(PlainLanguageRules.TRUE)) fillLanguagePathRepresentation(treeprops, sharedTreeSubgraph.rootNode);

        return treeprops;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, final String[] nodesDescriptions,
                               final float[] thresholds, final String[] splitColumns, final String[] naHandlings,
                               final int[][] levels, final float[] predictions,
                               final List<SharedTreeNode> nodesToTraverse, int pointer, boolean visitedRoot, 
                               String[][] domainValues) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> discoveredNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            if(visitedRoot){
                fillnodeDescriptions(node, nodesDescriptions, thresholds, splitColumns, levels, predictions,
                        naHandlings, pointer, domainValues);
            } else {
                StringBuilder rootDescriptionBuilder = new StringBuilder();
                rootDescriptionBuilder.append("*** WARNING: This property is deprecated! *** ");
                rootDescriptionBuilder.append("Root node has id ");
                rootDescriptionBuilder.append(node.getNodeNumber());
                rootDescriptionBuilder.append(" and splits on column '");
                rootDescriptionBuilder.append(node.getColName());
                rootDescriptionBuilder.append("'. ");
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
                discoveredNodes, pointer, true, domainValues);
    }

    private static List<Integer> extractInternalIds(TreeProperties properties) {
        int nodeId = 0;
        List<Integer> nodeIds = new ArrayList<>();
        nodeIds.add(nodeId);
        for (int i = 0; i < properties._leftChildren.length; i++) {
            if (properties._leftChildren[i] != -1) {
                nodeId++;
                nodeIds.add(properties._leftChildren[i]);
                properties._leftChildrenNormalized[i] = nodeId;
            } else {
                properties._leftChildrenNormalized[i] = -1;
            }

            if (properties._rightChildren[i] != -1) {
                nodeId++;
                nodeIds.add(properties._rightChildren[i]);
                properties._rightChildrenNormalized[i] = nodeId;
            } else {
                properties._rightChildrenNormalized[i] = -1;
            }
        }
        return nodeIds;
    }
    
    static String getCondition(SharedTreeNode node, String from) {
        StringBuilder sb = new StringBuilder();
        if (node.getDomainValues() != null) {
            sb.append("If (");
            sb.append(node.getColName());
            sb.append(" is in [");
            BitSet inclusiveLevels;
            if (from.equals("R")) {
                inclusiveLevels = node.getLeftChild().getInclusiveLevels();
            } else {
                inclusiveLevels = node.getRightChild().getInclusiveLevels();
            }
            if (inclusiveLevels != null) {
                String stringToParseInclusiveLevelsFrom = inclusiveLevels.toString();
                int inclusiveLevelsLength = stringToParseInclusiveLevelsFrom.length();
                if (inclusiveLevelsLength > 2) {
                    stringToParseInclusiveLevelsFrom = stringToParseInclusiveLevelsFrom.substring(1, inclusiveLevelsLength - 1);
                    String[] inclusiveLevelsStr = stringToParseInclusiveLevelsFrom.split(",");
                    for (String level : inclusiveLevelsStr) {
                        sb.append(node.getDomainValues()[Integer.parseInt(level.replaceAll("\\s", ""))]).append(" ");
                    }
            }
            } else {
                sb.append(" ");
            }
            sb.append("]) -> ");
        } else {
            if (Float.compare(node.getSplitValue(), Float.NaN) == 0) {
                String sign;
                if ("R".equals(from)) {
                    sign = " is not ";
                } else {
                    sign = " is ";
                }
                sb.append("If ( ").append(node.getColName()).append(sign).append("NaN )");
            } else {
                String sign;
                boolean useNan = false;
                String nanString = " or " + node.getColName() + " is NaN";
                if ("R".equals(from)) {
                    sign = " < ";
                    if (node.getLeftChild().isInclusiveNa()) {
                        useNan = true;
                    }
                } else {
                    sign = " >= ";
                    if (node.getRightChild().isInclusiveNa()) {
                        useNan = true;
                    }
                }
                sb.append("If ( " ).append(node.getColName()).append(sign).append(node.getSplitValue());
                if (useNan) {
                    sb.append(nanString);
                  
                }
                sb.append(" ) -> ");
            }
        }
        return sb.toString();
    }

    private static List<PathResult> findPaths(SharedTreeNode node) {
        if (node == null)
            return new ArrayList<>();
        
        List<PathResult> result = new ArrayList<>();

        List<PathResult> leftSubtree = findPaths(node.getLeftChild());
        List<PathResult> rightSubtree = findPaths(node.getRightChild());

        for (int i = 0; i < leftSubtree.size(); i++){
            PathResult leftResult = leftSubtree.get(i);
            PathResult newResult = leftResult;
            newResult.path.insert(0, getCondition(node, "R"));
            result.add(newResult);
        }

        for (int i = 0; i < rightSubtree.size(); i++){
            PathResult rightResult = rightSubtree.get(i);
            PathResult newResult = rightResult;
            newResult.path.insert(0, getCondition(node, "L"));
            result.add(newResult);
        }

        if (result.size() == 0) {
            result.add(new PathResult(node.getNodeNumber()));
            result.get(0).path.append("Prediction: ").append(node.getPredValue());
        }

        return result;
    }

    private static void fillLanguagePathRepresentation(TreeProperties properties, SharedTreeNode root) {
        List<Integer> nodeIds = extractInternalIds(properties);
        List<PathResult> paths = findPaths(root);
        for (PathResult path : paths) {
            properties._decisionPaths[nodeIds.indexOf(path.nodeId)] = path.path.toString();
        }
    }
    
    private static void fillnodeDescriptions(final SharedTreeNode node, final String[] nodeDescriptions,
                                             final float[] thresholds, final String[] splitColumns, final int[][] levels,
                                             final float[] predictions, final String[] naHandlings, final int pointer, final String[][] domainValues) {
        final StringBuilder nodeDescriptionBuilder = new StringBuilder();
        int[] nodeLevels = node.getParent().isBitset() ? extractNodeLevels(node) : null;
        nodeDescriptionBuilder.append("*** WARNING: This property is deprecated! *** ");
        nodeDescriptionBuilder.append("Node has id ");
        nodeDescriptionBuilder.append(node.getNodeNumber());
        if (node.getColName() != null && node.isLeaf()) {
            nodeDescriptionBuilder.append(" and splits on column '");
            nodeDescriptionBuilder.append(node.getColName());
            nodeDescriptionBuilder.append("'. ");
        } else {
            nodeDescriptionBuilder.append(" and is a terminal node. ");
        }

        fillNodeSplitTowardsChildren(nodeDescriptionBuilder, node);

        if (!Float.isNaN(node.getParent().getSplitValue())) {
            nodeDescriptionBuilder.append(" Parent node split threshold is ");
            nodeDescriptionBuilder.append(node.getParent().getSplitValue());
            nodeDescriptionBuilder.append(". Prediction: ");
            nodeDescriptionBuilder.append(node.getPredValue());
            nodeDescriptionBuilder.append(".");
        } else if (node.getParent().isBitset()) {
            nodeLevels = extractNodeLevels(node);
            nodeDescriptionBuilder.append(" Parent node split on column [");
            nodeDescriptionBuilder.append(node.getParent().getColName());
            if(nodeLevels != null) {
                nodeDescriptionBuilder.append("]. Inherited categorical levels from parent split: ");
                for (int nodeLevelsindex = 0; nodeLevelsindex < nodeLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getParent().getDomainValues()[nodeLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != nodeLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
            } else {
                nodeDescriptionBuilder.append("]. No categoricals levels inherited from parent.");
            }
        } else {
            nodeDescriptionBuilder.append("Split value is NA.");
        }

        nodeDescriptions[pointer] = nodeDescriptionBuilder.toString();
        splitColumns[pointer] = node.getColName();
        naHandlings[pointer] = getNaDirection(node);
        levels[pointer] = nodeLevels;
        predictions[pointer] = node.getPredValue();
        thresholds[pointer] = node.getSplitValue();
        domainValues[pointer] = node.getDomainValues();
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

            if (leftChildLevels != null) {
                for (int nodeLevelsindex = 0; nodeLevelsindex < leftChildLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getDomainValues()[leftChildLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != leftChildLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
            }
        }

        if (rightChild != null) {
            nodeDescriptionBuilder.append(". Right child node (");
            nodeDescriptionBuilder.append(rightChild.getNodeNumber());
            nodeDescriptionBuilder.append(") inherits categorical levels: ");

            if (rightChildLevels != null) {
                for (int nodeLevelsindex = 0; nodeLevelsindex < rightChildLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getDomainValues()[rightChildLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != rightChildLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
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
        public String _treeDecisionPath;
        public String[] _leafNodeAssignments;
        public String[] _decisionPaths;
        private int[] _leftChildrenNormalized;
        private int[] _rightChildrenNormalized;
        private String[][] _domainValues;

    }
}
