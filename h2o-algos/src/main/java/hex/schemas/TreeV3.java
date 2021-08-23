package hex.schemas;

import hex.tree.TreeHandler;
import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TreeV3 extends SchemaV3<Iced, TreeV3> {
    @API(required = true, direction = API.Direction.INPUT, help = "Key of the model the desired tree belongs to",level = API.Level.critical)
    public KeyV3.ModelKeyV3 model;

    @API(required = true, direction = API.Direction.INPUT, help = "Index of the tree in the model.", level = API.Level.critical)
    public int tree_number;

    @API(direction = API.Direction.INOUT, help = "Name of the class of the tree. Ignored for regression and binomial.", level = API.Level.critical)
    public String tree_class;

    @API(direction = API.Direction.INPUT, help = "Whether to generate plain language rules.", level = API.Level.critical, values = {"DEFAULT", "TRUE", "FALSE"})
    public TreeHandler.PlainLanguageRules plain_language_rules;

    @API(direction = API.Direction.OUTPUT, help = "Left child nodes in the tree")
    public int[] left_children;

    @API(direction = API.Direction.OUTPUT, help = "Right child nodes in the tree")
    public int[] right_children;

    @API(direction = API.Direction.OUTPUT, help = "Number of the root node")
    public int root_node_id;

    @API(direction = API.Direction.OUTPUT, help = "Split thresholds (numeric and possibly categorical columns)")
    public float[] thresholds;

    @API(direction = API.Direction.OUTPUT, help = "Names of the column of the split")
    public String[] features;

    @API(direction = API.Direction.OUTPUT, help = "Which way NA Splits (LEFT, RIGHT, NA)")
    public String[] nas;

    @API(direction = API.Direction.OUTPUT, help = "Description of the tree's nodes")
    public String[] descriptions;

    @API(direction = API.Direction.OUTPUT, help = "Categorical levels on the edge from the parent node")
    public int[][] levels;

    @API(direction = API.Direction.OUTPUT, help = "Prediction values on terminal nodes")
    public float[] predictions;

    @API(direction = API.Direction.OUTPUT, help = "Plain language rules representation of a trained decision tree")
    public String tree_decision_path;

    @API(direction = API.Direction.OUTPUT, help = "Plain language rules that were used in a particular prediction")
    public String[] decision_paths;
}
