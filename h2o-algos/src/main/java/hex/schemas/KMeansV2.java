package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.Key;
import water.api.API;
import water.api.JobV2;
import water.api.ModelParametersSchema;
import water.api.Schema;
import water.fvec.Frame;
import water.util.BeanUtils;
import water.util.DocGen.HTML;

import java.util.Properties;

public class KMeansV2 extends ModelBuilderSchema<KMeans,KMeansV2,KMeansV2.KMeansV2Parameters> {

  public static final class KMeansV2Parameters extends ModelParametersSchema<KMeansParameters, KMeansV2Parameters> {
    // Input fields
    @API(help = "Number of clusters", required = true)
    public int K;

    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    @API(help = "Normalize columns")
    public boolean normalize;

    @API(help = "RNG Seed")
    public long seed;

    @API(help = "Initialization mode", values = "random,plusplus,farthest")
    public KMeans.Initialization init;

    @Override public KMeansV2Parameters fillFromImpl(KMeansParameters parms) {
      super.fillFromImpl(parms);
      this.init = KMeans.Initialization.PlusPlus;
      return this;
    }

    public KMeansParameters createImpl() {
      KMeansParameters impl = new KMeansParameters();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      impl._init = KMeans.Initialization.PlusPlus;
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  @Override public KMeansV2Parameters createParametersSchema() { return new KMeansV2Parameters(); }

  // TODO: refactor ModelBuilder creation
  @Override public KMeans createImpl() {
    if( parameters.K < 2 || parameters.K > 9999999 ) throw new IllegalArgumentException("2<= K && K < 10000000");
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters==0 ) parameters.max_iters = 1000; // Default is 1000 max_iters
    if( parameters.seed == 0 ) parameters.seed = System.nanoTime();

    KMeansParameters parms = parameters.createImpl();
    return new KMeans(parms);
  }

  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
