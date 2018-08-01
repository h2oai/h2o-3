package hex.schemas;

import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TreeV3 extends SchemaV3<KeyV3.ModelKeyV3, TreeV3> {
    @API(required = true, direction = API.Direction.INPUT, help = "Key of the model the desired tree belongs to")
    public KeyV3.ModelKeyV3 key;

    @API(required = true, direction = API.Direction.INPUT, help = "Number of the tree in the model")
    public int treeNumber;

    @API(required =  true, direction = API.Direction.INPUT, help = "Tree class")
    public int treeClass;

    @API(direction = API.Direction.OUTPUT, help = "A traverseable representation of a tree")
    public String tree;

}
