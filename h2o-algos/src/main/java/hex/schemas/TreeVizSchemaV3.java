package hex.schemas;

import hex.api.TreeVizHandler;
import water.*;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TreeVizSchemaV3 extends SchemaV3<Iced, TreeVizSchemaV3> {
  // Output fields
  @API(help="some kind of tree", direction=API.Direction.OUTPUT)
  public CompressedTreesModelV3 trees;

  @API(help="key of the model", direction=API.Direction.INPUT)
  public KeyV3 modelKey;

  public TreeVizSchemaV3() {

  }

}
