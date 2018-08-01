package hex.tree;

import com.google.gson.*;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.utils.GenmodelBitSet;
import hex.schemas.TreeV3;
import water.api.Handler;

import java.lang.reflect.Type;

/**
 * Handling requests for various model trees
 */
public class TreeHandler extends Handler {


    private final Gson treeSerializer;

    public TreeHandler() {
        JsonSerializer sharedTreeSubgraphSerializer = new SharedTreeSubgraphSerializer();
        treeSerializer = new GsonBuilder()
                .registerTypeAdapter(SharedTreeSubgraph.class, sharedTreeSubgraphSerializer)
                .setPrettyPrinting() // Trees are small, pretty print does not introduce notable overhead
                .create();
    }

    public TreeV3 getTree(final int version, final TreeV3 args) {
        validateArgs(args);
        final SharedTreeModel model = (SharedTreeModel) args.key.key().get();
        if (model == null) throw new IllegalArgumentException("Unknown tree key: " + args.key.toString());

        final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
        final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[args.treeNumber][args.treeClass].get();
        final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[args.treeNumber][args.treeClass].get().
                toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

        args.tree = treeSerializer.toJson(sharedTreeSubgraph);
        return args;
    }

    /**
     * @param args An instance of {@link TreeV3} input arguments to validate
     */
    private void validateArgs(TreeV3 args) {
        if (args.treeNumber < 0) throw new IllegalArgumentException("Tree number must be greater than 0.");
        if (args.treeClass < 0) throw new IllegalArgumentException("Tree class must be greater than 0.");
    }

    /**
     * Google Gson {@link JsonSerializer} of {@link SharedTreeSubgraph}
     * Only a small subset of tree's properties is actually returned to the caller and there is no option for
     * annotations to be added on the properties of {@link SharedTreeSubgraph}.
     */
    private class SharedTreeSubgraphSerializer implements JsonSerializer<SharedTreeSubgraph> {

        @Override
        public JsonElement serialize(SharedTreeSubgraph src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject sharedTreeSubgraph = new JsonObject();
            JsonArray nodesArray = new JsonArray();

            for (SharedTreeNode node : src.nodesArray) {
                JsonObject serializedNode = new JsonObject();
                serializedNode.addProperty("number", node.getNodeNumber());
                serializedNode.addProperty("rightChildNumber", node.getRightChild().getNodeNumber());
                serializedNode.addProperty("leftChildNumber", node.getLeftChild().getNodeNumber());
                serializedNode.add("domain", context.serialize(node.getDomainValues()));
                serializedNode.addProperty("depth", node.getDepth());
                // Serialize the bits into separate properties or enclise them in a specific object, leaving the job to
                // GenmodelBitsetSerializer (undecided yet, cosmetic choice).
                serializedNode.add("attributes", context.serialize(node.getBs()));
            }


            sharedTreeSubgraph.add("nodes", nodesArray);
            return sharedTreeSubgraph;
        }
    }


}
