package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.Key;
import water.api.API;
import water.api.JobV2;
import water.api.ModelParametersBase;
import water.api.Schema;
import water.fvec.Frame;
import water.util.BeanUtils;
import water.util.DocGen.HTML;

import java.util.Properties;

public class KMeansV2 extends Schema<KMeans,KMeansV2> {
  // TODO: can we put these all into a ModelParametersSchema ?

  @API(help="Model builder parameters.")
  KMeansV2Parameters parameters;

  public static final class KMeansV2Parameters extends ModelParametersBase<KMeansParameters, KMeansV2Parameters> {
    // Input fields
    @API(help = "Input source frame", required = true)
    public Key src;

    @API(help = "Number of clusters", required = true)
    public int K;

    @API(help = "Normalize columns")
    public boolean normalize;

    @API(help = "Max Iterations")
    public int max_iters;

    @API(help = "RNG Seed")
    public long seed;

    @API(help = "Initialization mode", values = "random,plusplus,farthest")
    public KMeans.Initialization init;

    // Output fields
    @API(help = "Job Key")
    Key job;

    public KMeansV2Parameters fillFromImpl(KMeansParameters parms) {
      BeanUtils.copyProperties(this, parms, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
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

  // TODO: move into builder superclass:
  public KMeansV2 fillFromParms(Properties parms) {
    this.parameters = new KMeansV2Parameters();
    this.parameters.fillFromParms(parms);
    return this;
  }



  // Version&Schema-specific filling into the handler
  @Override public KMeans createImpl() {
    if( parameters.K < 2 || parameters.K > 9999999 ) throw new IllegalArgumentException("2<= K && K < 10000000");
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters==0 ) parameters.max_iters = 1000; // Default is 1000 max_iters
    if( parameters.seed == 0 ) parameters.seed = System.nanoTime();

    KMeansParameters parms = parameters.createImpl();
    return new KMeans(parms);
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansV2 fillFromImpl(KMeans builder) {
    //    job = h._job._key;  // TODO: what?

    parameters = new KMeansV2Parameters();
    parameters.fillFromImpl(builder._parms);
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("KMeans Started");
    String url = JobV2.link(parameters.job);
    return ab.href("Poll",url,url);
  }

  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?src="+fr._key; }
}
