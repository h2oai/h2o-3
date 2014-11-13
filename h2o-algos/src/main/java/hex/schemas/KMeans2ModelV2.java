package hex.schemas;

import hex.kmeans2.KMeans2Model;
import water.H2O;
import water.api.*;
import water.util.PojoUtils;

public class KMeans2ModelV2 extends ModelSchema<KMeans2Model, KMeans2Model.KMeans2Parameters, KMeans2Model.KMeans2Output, KMeans2ModelV2 > {

  public static final class KMeans2ModelOutputV2 extends ModelOutputSchema<KMeans2Model.KMeans2Output, KMeans2ModelOutputV2> {
    // Output fields
    @API(help="Iterations executed") public double iters;
    @API(help="Cluster centers") public double clusters[/*K*/][/*N*/];
    @API(help="Mean Squared Error") public double mse;

    @Override public KMeans2Model.KMeans2Output createImpl() {
      KMeans2Model.KMeans2Output impl = new KMeans2Model.KMeans2Output(null);
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public KMeans2ModelOutputV2 fillFromImpl( KMeans2Model.KMeans2Output impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // KMeans2ModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeans2V2.KMeans2ParametersV2 createParametersSchema() { return new KMeans2V2.KMeans2ParametersV2(); }
  public KMeans2ModelOutputV2 createOutputSchema() { return new KMeans2ModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public KMeans2Model createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the impl
  @Override public KMeans2ModelV2 fillFromImpl( KMeans2Model m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: KMeans2Model kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return super.fillFromImpl(m);
  }
}
