package hex.schemas;

import hex.splitframe.SplitFrameModel;
import water.H2O;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.FrameV2;
import water.util.PojoUtils;

public class SplitFrameModelV2 extends ModelSchema<SplitFrameModel, SplitFrameModelV2, SplitFrameModel.SplitFrameParameters, SplitFrameModel.SplitFrameOutput> {

  public static final class SplitFrameModelOutputV2 extends ModelOutputSchema<SplitFrameModel.SplitFrameOutput, SplitFrameModelOutputV2> {

    // Output fields
    @API(help="Output frames for each output split part")
    public FrameV2[] splits;

    @Override public SplitFrameModel.SplitFrameOutput createImpl() {
      SplitFrameModel.SplitFrameOutput impl = new SplitFrameModel.SplitFrameOutput(null);
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public SplitFrameModelOutputV2 fillFromImpl( SplitFrameModel.SplitFrameOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // SplitFrameModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public SplitFrameV2.SplitFrameParametersV2 createParametersSchema() { return new SplitFrameV2.SplitFrameParametersV2(); }
  public SplitFrameModelOutputV2 createOutputSchema() { return new SplitFrameModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public SplitFrameModel createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the impl
  @Override public SplitFrameModelV2 fillFromImpl( SplitFrameModel m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: SplitFrameModel kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return super.fillFromImpl(m);
  }
}
