package hex.schemas;

import hex.kmeans2.KMeans2;
import hex.kmeans2.KMeans2Model;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class KMeans2V2 extends ModelBuilderSchema<KMeans2,KMeans2V2,KMeans2V2.KMeans2ParametersV2> {

  public static final class KMeans2ParametersV2 extends ModelParametersSchema<KMeans2Model.KMeans2Parameters, KMeans2ParametersV2> {
    public String[] fields() { return new String[] {"training_frame", "max_iters", "K"}; }

    // Input fields
    @API(help="Maximum training iterations.") public int max_iters;
    @API(help="K") public int K;
  } // KMeans2ParametersV2


  //==========================
  // Custom adapters go here

  // Return a URL to invoke KMeans2 on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans2?training_frame="+fr._key; }
}
