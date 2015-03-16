package hex.schemas;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class QuantileV2 extends ModelBuilderSchema<Quantile,QuantileV2,QuantileV2.QuantileParametersV2> {

  public static final class QuantileParametersV2 extends ModelParametersSchema<QuantileModel.QuantileParameters, QuantileParametersV2> {
    static public String[] own_fields = new String[] {"probs"};

    // Input fields
    @API(help="Probabilities for quantiles")  public double probs[];
  } // QuantileParametersV2
}
