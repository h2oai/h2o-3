package hex.tree;

import com.google.gson.*;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.utils.GenmodelBitSet;
import hex.schemas.TreeV3;
import water.Key;
import water.api.Handler;

import java.lang.reflect.Type;

public class TreeHandler extends Handler {


    private final Gson treeSerializer;

    public TreeHandler() {
        JsonSerializer sharedTreeSubgraphSerializer = new SharedTreeSubgraphSerializer();
        JsonSerializer genModelBitSerializer = new GenmodelBitSetSerializer();
        treeSerializer = new GsonBuilder()
                .registerTypeAdapter(SharedTreeSubgraph.class, sharedTreeSubgraphSerializer)
                .registerTypeAdapter(GenmodelBitSet.class, genModelBitSerializer)
                .setPrettyPrinting() // Trees are small, pretty print does not introduce notable overhead
                .create();
    }

    public TreeV3 getTree(int version, TreeV3 args) {

        final Key<CompressedTree> key = null;
        final CompressedTree compressedTree = key.get();
        if (compressedTree == null) throw new IllegalArgumentException("Unknown tree key: " + key.toString());

        final SharedTreeSubgraph sharedTreeSubgraph = null; // Use args & constructor created by Michal K.
        args.tree = treeSerializer.toJson(sharedTreeSubgraph);
        return args;
    }


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

            }

            sharedTreeSubgraph.add("nodes", nodesArray);
            return sharedTreeSubgraph;
        }
    }

    private class GenmodelBitSetSerializer implements JsonSerializer<GenmodelBitSet> {

        @Override
        public JsonElement serialize(GenmodelBitSet src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject serializedBits = new JsonObject();

            return serializedBits;
        }
    }


}
