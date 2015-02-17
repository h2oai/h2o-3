package hex.schemas;

import hex.grep.GrepModel;
import water.H2O;
import water.api.*;
import water.util.PojoUtils;

public class GrepModelV2 extends ModelSchema<GrepModel, GrepModelV2, GrepModel.GrepParameters, GrepV2.GrepParametersV2, GrepModel.GrepOutput, GrepModelV2.GrepModelOutputV2> {

  public static final class GrepModelOutputV2 extends ModelOutputSchema<GrepModel.GrepOutput, GrepModelOutputV2> {
    // Output fields
    // Assume small-data results: string matches only
    @API(help="Matching strings") public String[] matches;
    @API(help="Byte offsets of matches") public long[] offsets;

    @Override public GrepModel.GrepOutput createImpl() {
      GrepModel.GrepOutput impl = new GrepModel.GrepOutput(null);
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public GrepModelOutputV2 fillFromImpl( GrepModel.GrepOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // GrepModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GrepV2.GrepParametersV2 createParametersSchema() { return new GrepV2.GrepParametersV2(); }
  public GrepModelOutputV2 createOutputSchema() { return new GrepModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public GrepModel createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the impl
  @Override public GrepModelV2 fillFromImpl( GrepModel m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: GrepModel kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return super.fillFromImpl(m);
  }
}
