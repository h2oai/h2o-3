package hex.schemas;

import hex.tree.TreeStats;
import water.api.API;
import water.api.SchemaV3;

public class TreeStatsV3 extends SchemaV3<TreeStats, TreeStatsV3> {
  // TODO: no CamelCase
  @API(help="minDepth")
  public int min_depth;

  @API(help="maxDepth")
  public int max_depth;

  @API(help="meanDepth")
  public float mean_depth;

  @API(help="minLeaves")
  public int min_leaves;

  @API(help="maxLeaves")
  public int max_leaves;

  @API(help="meanLeaves")
  public float mean_leaves;
}
