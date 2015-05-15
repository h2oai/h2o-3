package hex.schemas;

import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import water.api.*;

public class SharedTreeV3<B extends SharedTree, S extends SharedTreeV3<B,S,P>, P extends SharedTreeV3.SharedTreeParametersV3> extends SupervisedModelBuilderSchema<B,S,P> {

  public static class SharedTreeParametersV3<P extends SharedTreeParameters, S extends SharedTreeParametersV3<P, S>> extends SupervisedModelParametersSchema<P, S> {
    static public String[] own_fields = new String[] {
      "ntrees", "max_depth", "min_rows", "nbins", "r2_stopping", "seed"
    };

    @API(help="Number of trees.", gridable = true)
    public int ntrees;

    @API(help="Maximum tree depth.", gridable = true)
    public int max_depth;

    @API(help="Fewest allowed observations in a leaf (in R called 'nodesize').", gridable = true)
    public int min_rows;

    @API(help="Build a histogram of this many bins, then split at the best point", gridable = true)
    public int nbins;

    @API(help="Stop making trees when the r^2 metric equals or exceeds this")
    public double r2_stopping;

    @API(help = "Seed for pseudo random number generator (if applicable)", level = API.Level.expert)
    public long seed;
  }
}
