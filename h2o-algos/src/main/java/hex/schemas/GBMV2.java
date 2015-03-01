package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.api.API;
import water.api.SupervisedModelParametersSchema;
import hex.schemas.SharedTreeV2;                        // Yes, this is needed.  Compiler bug.
import hex.schemas.SharedTreeV2.SharedTreeParametersV2; // Yes, this is needed.  Compiler bug.

public class GBMV2 extends SharedTreeV2<GBM,GBMV2,GBMV2.GBMParametersV2> {

  public static final class GBMParametersV2 extends SharedTreeParametersV2<GBMParameters, GBMParametersV2> {
    static public String[] own_fields = new String[] {
        "ntrees",
        "max_depth",
        "min_rows",
        "nbins",
        "learn_rate",
        "loss",
        "seed"
    };

    // Input fields
    @API(help="Learning rate from 0.0 to 1.0")
    public float learn_rate;

    @API(help = "Loss function", values = { "AUTO", "bernoulli", "multinomial", "gaussian" })
    public GBMParameters.Family loss;

  }
}
