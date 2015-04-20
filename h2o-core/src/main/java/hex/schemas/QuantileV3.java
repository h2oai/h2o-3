package hex.schemas;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class QuantileV3 extends ModelBuilderSchema<Quantile,QuantileV3,QuantileV3.QuantileParametersV2> {

  public static final class QuantileParametersV2 extends ModelParametersSchema<QuantileModel.QuantileParameters, QuantileParametersV2> {
    static public String[] own_fields = new String[] {"probs"};

    // Input fields
    @API(help="Probabilities for quantiles")  public double probs[];
    @API(help="How to combine quantiles for even sample sizes") public QuantileModel.CombineMethod combine_method;
  } // QuantileParametersV2
}
