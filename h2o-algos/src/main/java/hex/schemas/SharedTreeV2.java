package hex.schemas;

import hex.AUC;
import hex.ConfusionMatrix2;
import hex.VarImp;
import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import hex.tree.TreeStats;
import water.api.API;
import water.api.ModelParametersSchema;

public abstract class SharedTreeV2 extends ModelBuilderSchema<SharedTree,SharedTreeV2,SharedTreeV2.SharedTreeParametersV2> {

  public abstract static class SharedTreeParametersV2 extends ModelParametersSchema<SharedTreeParameters, SharedTreeParametersV2> {
    static public String[] own_fields = new String[] {
      "ntrees", "treeStats", "r2", "mse_train", "mse_test", "cm", "auc", "varimp"
    };

    // Output fields
    /** Number of trees in model */
    public int ntrees;

    /** More indepth tree stats */
    public TreeStats treeStats;

    /** r2 metric on validation set: 1-(MSE(model) / MSE(mean)) */
    public double r2;

    /** Train and test errors per-tree (scored).  Zero index is the no-tree
     *  error, guessing only the class distribution.  Not all trees are
     *  scored, NaN represents trees not scored. */
    public double mse_train[/*_ntrees+1*/];
    public double mse_test [/*_ntrees+1*/];

    /** Confusion Matrix for classification models, or null otherwise */
    public ConfusionMatrix2 cm;

    /** AUC for binomial models, or null otherwise */
    public AUC auc;

    /** Variable Importance, if asked for */
    public VarImp varimp;

    @Override public SharedTreeParametersV2 fillFromImpl(SharedTreeParameters parms) {
      super.fillFromImpl(parms);
      return this;
    }

  }
}
