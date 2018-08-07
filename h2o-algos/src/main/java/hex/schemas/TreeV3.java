package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TreeV3 extends SchemaV3<Iced, TreeV3> {
    @API(required = true, direction = API.Direction.INPUT, help = "Key of the model the desired tree belongs to",level = API.Level.critical)
    public KeyV3.ModelKeyV3 model;

    @API(required = true, direction = API.Direction.INPUT, help = "Number of the tree in the model", level = API.Level.critical)
    public int tree_number;

    @API(direction = API.Direction.INPUT, help = "Tree class (if applicable)", level = API.Level.critical)
    public int tree_class;

    @API(direction = API.Direction.OUTPUT, help = "Left child nodes in the tree")
    public int[] left_children;

    @API(direction = API.Direction.OUTPUT, help = "Right child nodes in the tree")
    public int[] right_children;

    @API(direction = API.Direction.OUTPUT, help = "Number of the root node")
    public int root_node_id;

    @API(direction = API.Direction.OUTPUT, help = "Description of the tree's nodes")
    public String[] descriptions;

}
