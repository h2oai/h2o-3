package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.BeanUtils;

public class KMeansV2 extends ModelBuilderSchema<KMeans,KMeansV2,KMeansV2.KMeansParametersV2> {

  public static final class KMeansParametersV2 extends ModelParametersSchema<KMeansParameters, KMeansParametersV2> {
    public String[] fields() { return new String[] {"K", "max_iters", "normalize", "seed", "init" }; }

    // TODO: we do defaults both here and in the impl; that's no good.

    // Input fields
    @API(help = "Number of clusters", required = true, validation = { "K > 0", "K < 100000" })
    public int K;

    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    @API(help = "Normalize columns", level = API.Level.secondary)
    public boolean normalize = true;

    @API(help = "RNG Seed", level = API.Level.expert /* tested, works: , dependsOn = {"K", "max_iters"} */ )
    public long seed;

    @API(help = "Initialization mode", values = { "None", "PlusPlus", "Furthest" }) // TODO: pull out of enum class. . .
    public KMeans.Initialization init;

    @Override public KMeansParametersV2 fillFromImpl(KMeansParameters parms) {
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

  @Override public KMeansParametersV2 createParametersSchema() { return new KMeansParametersV2(); }

  // TODO: refactor ModelBuilder creation
  // TODO: defaults should only be in the impl, not duplicated in the API layer
  @Override public KMeans createImpl() {
    if( parameters.K < 2 || parameters.K > 9999999 ) throw new IllegalArgumentException("2<= K && K < 10000000");
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters==0 ) parameters.max_iters = 1000; // Default is 1000 max_iters
    if( parameters.seed == 0 ) parameters.seed = System.nanoTime();

    KMeansParameters parms = parameters.createImpl();
    return new KMeans(parms);
  }
  public KMeans createImpl(Frame fr) { parameters.src = fr._key; return createImpl(); }

  // TODO: UGH
  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
