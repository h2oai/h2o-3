package hex.schemas;

import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import water.api.*;

public abstract class SharedTreeV2<B extends SharedTree, S extends SharedTreeV2<B,S,P>, P extends SharedTreeV2.SharedTreeParametersV2> extends SupervisedModelBuilderSchema<B,S,P> {

  public abstract static class SharedTreeParametersV2<P extends SharedTreeParameters, S extends SharedTreeParametersV2<P, S>> extends SupervisedModelParametersSchema<P, S> {
    static public String[] own_fields = new String[] {
      "ntrees", "max_depth", "min_rows", "nbins", "seed"
    };

    @API(help="Number of trees.  Grid Search, comma sep values:50,100,150,200")
    public int ntrees;

    @API(help="Maximum tree depth.  Grid Search, comma sep values:5,7")
    public int max_depth;

    @API(help="Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values")
    public int min_rows;

    @API(help="Build a histogram of this many bins, then split at the best point")
    public int nbins;

    @API(help = "RNG Seed for balancing classes", level = API.Level.expert)
    public long seed;

    @API(help="More in-depth tree stats", direction=API.Direction.OUTPUT)
    public TreeStatsV3 treeStats;

    @API(help="r2 metric on validation set: 1-(MSE(model) / MSE(mean))", direction=API.Direction.OUTPUT)
    public double r2;

    /** Train and validation errors per-tree (scored).  Zero index is the no-tree
     *  error, guessing only the class distribution.  Not all trees are
     *  scored, NaN represents trees not scored.
     */
    @API(help="Training set error per-tree (scored).", direction=API.Direction.OUTPUT)
    public double mse_train[/*_ntrees+1*/];

    @API(help="Validation set error per-tree (scored).", direction=API.Direction.OUTPUT)
    public double mse_valid[/*_ntrees+1*/];

    @API(help="Confusion Matrix for classification models, or null otherwise", direction=API.Direction.OUTPUT)
    public ConfusionMatrixV3 cm;

    @API(help="AUC for binomial models, or null otherwise", direction=API.Direction.OUTPUT)
    public AUCV3 auc;

    @API(help="Variable Importance", direction=API.Direction.OUTPUT)
    public VarImpV2 varimp;
  }
}
