package hex.schemas;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel.DRFParameters;
import water.api.API;

public class DRFV3 extends SharedTreeV3<DRF,DRFV3, DRFV3.DRFParametersV3> {

  public static final class DRFParametersV3 extends SharedTreeV3.SharedTreeParametersV3<DRFParameters, DRFParametersV3> {
    static public String[] own_fields = new String[] {
        "mtries",
        "sample_rate",
        "build_tree_one_node"
    };

    // Input fields
    @API(help = "Number of columns to randomly select at each level, or -1 for sqrt(#cols)", gridable = true)
    public int mtries;

    @API(help = "Sample rate, from 0. to 1.0", gridable = true)
    public float sample_rate;

    @API(help="Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.")
    public boolean build_tree_one_node;
  }
}
