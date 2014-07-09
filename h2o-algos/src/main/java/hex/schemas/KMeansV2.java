package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.Key;
import water.api.API;
import water.api.JobV2;
import water.api.Schema;
import water.fvec.Frame;
import water.util.DocGen.HTML;

public class KMeansV2 extends Schema<KMeans,KMeansV2> {
  // TODO: can we put these all into a ModelParametersSchema ?
  // Input fields
  @API(help="Input source frame",required=true)
  public Key src;

  @API(help="Number of clusters",required=true)
  public int K;

  @API(help="Normalize columns")
  public boolean normalize;

  @API(help="Max Iterations")
  public int max_iters;

  @API(help="RNG Seed")
  public long seed;

  @API(help="Initialization mode",values="random,plusplus,farthest")
  public KMeans.Initialization init;

  // Output fields
  @API(help="Job Key")
  Key job;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  @Override public KMeans createImpl() {
    KMeansParameters parms = new KMeansModel.KMeansParameters();

    parms._src = src;

    if( K < 2 || K > 9999999 ) throw new IllegalArgumentException("2<= K && K < 10000000");
    parms._K = K;

    parms._normalize = normalize;

    parms._init = init = KMeans.Initialization.PlusPlus;

    if( max_iters < 0 || max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( max_iters==0 ) max_iters = 1000; // Default is 1000 max_iters
    parms._max_iters = max_iters;

    if( seed == 0 ) seed = System.nanoTime();
    parms._seed = seed;

    return new KMeans(parms);
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansV2 fillFromImpl(KMeans builder) {
    //    job = h._job._key;  // TODO: what?

    src = builder._parms._src;
    K = builder._parms._K;
    normalize = builder._parms._normalize;
    max_iters = builder._parms._max_iters;
    seed = builder._parms._seed;
    init = builder._parms._init = KMeans.Initialization.PlusPlus;

    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("KMeans Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
