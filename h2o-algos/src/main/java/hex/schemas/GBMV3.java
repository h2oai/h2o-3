package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.api.API;


public class GBMV3 extends SharedTreeV3<GBM,GBMV3,GBMV3.GBMParametersV3> {

  public static final class GBMParametersV3 extends SharedTreeV3.SharedTreeParametersV3<GBMParameters, GBMParametersV3> {
    static public String[] own_fields = new String[] {
        "learn_rate",
        "distribution",
    };

    // Input fields
    @API(help="Learning rate from 0.0 to 1.0", gridable = true)
    public float learn_rate;

    @API(help = "Distribution function", values = { "AUTO", "bernoulli", "multinomial", "gaussian", "poisson", "gamma", "tweedie" }, gridable = true)
    public GBMParameters.Family distribution;
  }
}
