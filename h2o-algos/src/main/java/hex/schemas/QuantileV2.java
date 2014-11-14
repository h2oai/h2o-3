package hex.schemas;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class QuantileV2 extends ModelBuilderSchema<Quantile,QuantileV2,QuantileV2.QuantileParametersV2> {

  public static final class QuantileParametersV2 extends ModelParametersSchema<QuantileModel.QuantileParameters, QuantileParametersV2> {
    static public String[] own_fields = new String[] {"probs"};

    // Input fields
    @API(help="Probabilities for quantiles")  public double probs[];
  } // QuantileParametersV2


  //==========================
  // Custom adapters go here

  // Return a URL to invoke Quantile on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Quantile?training_frame="+fr._key; }
}
