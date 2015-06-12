package hex.schemas;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel.DRFParameters;
import water.api.API;

public class DRFV3 extends SharedTreeV3<DRF,DRFV3, DRFV3.DRFParametersV3> {

  public static final class DRFParametersV3 extends SharedTreeV3.SharedTreeParametersV3<DRFParameters, DRFParametersV3> {
    static public String[] own_fields = new String[] {
        "mtries",
        "sample_rate",
        "build_tree_one_node",
        "binomial_double_trees"
    };

    // Input fields
    @API(help = "Number of variables randomly sampled as candidates at each split. If set to -1, defaults to sqrt{p} for classification and p/3 for regression (where p is the # of predictors", gridable = true)
    public int mtries;

    @API(help = "Sample rate, from 0. to 1.0", gridable = true)
    public float sample_rate;

    @API(help="Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.", level = API.Level.secondary)
    public boolean build_tree_one_node;

    @API(help="For binary classification: Build 2x as many trees (one per class) - can lead to higher accuracy.", level = API.Level.secondary)
    public boolean binomial_double_trees;
  }
}
