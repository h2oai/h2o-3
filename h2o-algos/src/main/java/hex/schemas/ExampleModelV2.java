package hex.schemas;

import hex.example.ExampleModel;
import water.H2O;
import water.api.*;
import water.util.PojoUtils;

public class ExampleModelV2 extends ModelSchema<ExampleModel, ExampleModel.ExampleParameters, ExampleModel.ExampleOutput, ExampleModelV2 > {

  public static final class ExampleModelOutputV2 extends ModelOutputSchema<ExampleModel.ExampleOutput, ExampleModelOutputV2> {
    // Output fields
    @API(help="Iterations executed") public int iters;
    @API(help="") public double[] maxs;

    @Override public ExampleModel.ExampleOutput createImpl() {
      ExampleModel.ExampleOutput impl = new ExampleModel.ExampleOutput(null);
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public ExampleModelOutputV2 fillFromImpl( ExampleModel.ExampleOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // ExampleModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public ExampleV2.ExampleParametersV2 createParametersSchema() { return new ExampleV2.ExampleParametersV2(); }
  public ExampleModelOutputV2 createOutputSchema() { return new ExampleModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public ExampleModel createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the impl
  @Override public ExampleModelV2 fillFromImpl( ExampleModel m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: ExampleModel kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return super.fillFromImpl(m);
  }
}
