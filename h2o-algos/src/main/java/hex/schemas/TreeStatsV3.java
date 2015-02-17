package hex.schemas;

import hex.tree.TreeStats;
import water.api.API;
import water.api.Schema;

public class TreeStatsV3 extends Schema<TreeStats, TreeStatsV3> {
  // TODO: no CamelCase
  @API(help="minDepth")
  public int   minDepth;

  @API(help="maxDepth")
  public int   maxDepth;

  @API(help="meanDepth")
  public float meanDepth;

  @API(help="minLeaves")
  public int   minLeaves;

  @API(help="maxLeaves")
  public int   maxLeaves;

  @API(help="meanLeaves")
  public float meanLeaves;
}
