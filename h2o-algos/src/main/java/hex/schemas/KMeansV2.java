package hex.schemas;

import water.api.Schema;
import water.api.API;
import water.Key;
import water.fvec.Frame;

public class KMeansV2 extends Schema<KMeansHandler,KMeansV2> {

  // Input fields
  @API(help="Input source frame",required=true)
  public Key src;

  @API(help="Number of clusters",required=true)
  public int K;

  // Output fields
  @API(help="Job Key")
  Key job;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public KMeansV2 fillInto( KMeansHandler h ) {
    h._src = src;
    h._K = K;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansV2 fillFrom( KMeansHandler h ) {
    job = h._job;
    return this;
  }

  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
