package hex.schemas;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel.DRFParameters;
import water.api.API;
import water.api.SupervisedModelParametersSchema;
import hex.schemas.SharedTreeV2;                        // Yes, this is needed.  Compiler bug.
import hex.schemas.SharedTreeV2.SharedTreeParametersV2; // Yes, this is needed.  Compiler bug.

public class DRFV2 extends SharedTreeV2<DRF,DRFV2,DRFV2.DRFParametersV2> {

  public static final class DRFParametersV2 extends SharedTreeParametersV2<DRFParameters, DRFParametersV2> {
    static public String[] own_fields = new String[] {
        "ntrees",
        "max_depth",
        "min_rows",
        "nbins",
        "mtries",
        "sample_rate",
        "seed",
        "do_grpsplit",
        "build_tree_one_node"
    };

    // Input fields
    @API(help = "Columns to randomly select at each level, or -1 for sqrt(#cols)")
    int mtries;

    @API(help = "Sample rate, from 0. to 1.0")
    float sample_rate;

    @API(help = "Check non-contiguous group splits for categorical predictors")
    boolean do_grpsplit;

    @API(help="Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.")
    public boolean build_tree_one_node;
  }
}
