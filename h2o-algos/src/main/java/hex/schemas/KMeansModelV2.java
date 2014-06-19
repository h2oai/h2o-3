package hex.schemas;

import hex.kmeans.KMeansModel;
import water.Key;
import water.api.API;
import water.api.Handler;
import water.api.Schema;
import water.H2O;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends Schema {

  // Input fields
  @API(help="KMeans Model to inspect",required=true)
  Key key;

  // Output fields
  @API(help="KMeans Model")
  KMeansModel kmeansModel;

  transient KMeansModel _kmm;
  public KMeansModelV2( KMeansModel kmm ) { _kmm = kmm;  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public KMeansModelV2 fillInto( Handler h ) {
    throw H2O.fail();
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansModelV2 fillFrom( Handler h ) {
    kmeansModel = _kmm;
    return this;
  }

  //@Override public HTML writeHTML_impl( HTML ab ) {
  //  ab.title("KMeansModel Viewer");
  //  return ab;
  //}
}
