package hex.schemas;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class QuantileV3 extends ModelBuilderSchema<Quantile,QuantileV3,QuantileV3.QuantileParametersV3> {

  public static final class QuantileParametersV3 extends ModelParametersSchema<QuantileModel.QuantileParameters, QuantileParametersV3> {
    static public String[] fields = new String[] {"probs", "combine_method"};

    // Input fields
    @API(help="Probabilities for quantiles")  public double probs[];
    @API(help="How to combine quantiles for even sample sizes", values={"INTERPOLATE", "AVG", "LO", "HI"}) public QuantileModel.CombineMethod combine_method;
  } // QuantileParametersV2
}
