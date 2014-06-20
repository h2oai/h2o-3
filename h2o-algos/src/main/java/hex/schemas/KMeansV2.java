package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.Key;
import water.api.API;
import water.api.JobPollV2;
import water.api.Schema;
import water.fvec.Frame;
import water.util.DocGen.HTML;

public class KMeansV2 extends Schema<KMeansHandler,KMeansV2> {

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
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public KMeansV2 fillInto( KMeansHandler h ) {
    h._parms = new KMeansModel.KMeansParameters();
    h._parms._src = src;

    if( K < 2 || K > 9999999 ) throw new IllegalArgumentException("2<= K && K < 10000000");
    h._parms._K = K;

    h._parms._normalize = normalize;

    h._parms._init = init = KMeans.Initialization.PlusPlus;

    if( max_iters < 0 || max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( max_iters==0 ) max_iters = 1000; // Default is 1000 max_iters
    h._parms._max_iters = max_iters;

    if( seed == 0 ) seed = System.nanoTime();
    h._parms._seed = seed;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansV2 fillFrom( KMeansHandler h ) {
    job = h._job._key;
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("KMeans Started");
    String url = JobPollV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
