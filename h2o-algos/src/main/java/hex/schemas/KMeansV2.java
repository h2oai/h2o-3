package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.BeanUtils;

public class KMeansV2 extends ModelBuilderSchema<KMeans,KMeansV2,KMeansV2.KMeansV2Parameters> {

  public static final class KMeansV2Parameters extends ModelParametersSchema<KMeansParameters, KMeansV2Parameters> {
    public String[] fields() { return new String[] {"K", "max_iters", "normalize", "seed", "init" }; }

    // Input fields
    @API(help = "Number of clusters", required = true, validation = { "K > 0", "K < 100000" })
    public int K;

    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    @API(help = "Normalize columns", level = API.Level.secondary)
    public boolean normalize;

    @API(help = "RNG Seed", level = API.Level.expert /* tested, works: , dependsOn = {"K", "max_iters"} */ )
    public long seed;

    @API(help = "Initialization mode", values = { "None", "PlusPlus", "Furthest" }) // TODO: pull out of enum class. . .
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
