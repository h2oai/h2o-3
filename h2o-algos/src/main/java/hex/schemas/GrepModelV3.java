package hex.schemas;

import hex.grep.GrepModel;
import water.H2O;
import water.api.*;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.util.PojoUtils;

public class GrepModelV3 extends ModelSchemaV3<GrepModel, GrepModelV3, GrepModel.GrepParameters, GrepV3.GrepParametersV3, GrepModel.GrepOutput, GrepModelV3.GrepModelOutputV3> {

  public static final class GrepModelOutputV3 extends ModelOutputSchemaV3<GrepModel.GrepOutput, GrepModelOutputV3> {
    // Output fields
    // Assume small-data results: string matches only
    @API(help="Matching strings") public String[] matches;
    @API(help="Byte offsets of matches") public long[] offsets;

    @Override public GrepModel.GrepOutput createImpl() {
      GrepModel.GrepOutput impl = new GrepModel.GrepOutput(null);
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public GrepModelOutputV3 fillFromImpl( GrepModel.GrepOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // GrepModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchemaV3, using reflection on the type parameters.
  public GrepV3.GrepParametersV3 createParametersSchema() { return new GrepV3.GrepParametersV3(); }
  public GrepModelOutputV3 createOutputSchema() { return new GrepModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public GrepModel createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the impl
  @Override public GrepModelV3 fillFromImpl( GrepModel m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: GrepModel kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return super.fillFromImpl(m);
  }
}
