package hex.schemas;

import hex.kmeans.KMeansModel;
import water.DKV;
import water.Key;
import water.api.*;
import water.api.ModelSchema;
import water.fvec.Frame;
import water.util.BeanUtils;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends ModelSchema<KMeansModel, KMeansModel.KMeansParameters, KMeansModel.KMeansOutput, KMeansModelV2> {

  public static final class KMeansModelV2Output extends ModelOutputSchema<KMeansModel.KMeansOutput, KMeansModelV2Output> {
    // Input fields
    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    // Output fields
    @API(help="Clusters[K][features]")
    public double[/*K*/][/*features*/] clusters;

    @API(help="Rows[K]")
    public long[/*K*/]  rows;

    @API(help="Mean Square Error per cluster")
    public double[/*K*/] mses;   // Per-cluster MSE, variance

    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance

    @API(help="Iterations executed")
    public double iters;

    @Override public KMeansModel.KMeansOutput createImpl() {
      KMeansModel.KMeansOutput impl = new KMeansModel.KMeansOutput();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    // Version&Schema-specific filling from the handler
    @Override public KMeansModelV2Output fillFromImpl( KMeansModel.KMeansOutput impl) {
      // TODO: Weh?
      // if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
      // InspectHandler ih = (InspectHandler)h;
      // KMeansModel kmm = ih._val.get();
      BeanUtils.copyProperties(this, impl, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }


  } // KMeansModelV2Output

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeansV2.KMeansV2Parameters createParametersSchema() { return new KMeansV2.KMeansV2Parameters(); }
  public KMeansModelV2Output createOutputSchema() { return new KMeansModelV2Output(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public KMeansModel createImpl() {
    KMeansV2.KMeansV2Parameters p = ((KMeansV2.KMeansV2Parameters)this.parameters);
    KMeansModel.KMeansParameters parms = p.createImpl();
    return new KMeansModel( key, (Frame)DKV.get(p.src).get(), parms, new KMeansModel.KMeansOutput(), 0 );
  }

  // Version&Schema-specific filling from the impl
  @Override public KMeansModelV2 fillFromImpl( KMeansModel kmm ) {
    return super.fillFromImpl(kmm);
  }
}
