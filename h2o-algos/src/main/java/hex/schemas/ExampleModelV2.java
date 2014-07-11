package hex.schemas;

import hex.example.ExampleModel;
import water.H2O;
import water.Key;
import water.Model;
import water.api.*;
import water.util.BeanUtils;

public class ExampleModelV2 extends ModelBase<ExampleModel, ExampleModel.ExampleParameters, ExampleModel.ExampleOutput, ExampleModelV2 > {

  // TODO: move this?
  // Input fields
  @API(help="Example Model to inspect",required=true)
  Key key;


  public static final class ExampleModelV2Output extends ModelOutputBase<ExampleModel.ExampleOutput, ExampleModelV2Output> {
    @API(help="Iterations executed")
    public double iters;

    // Output fields
    @API(help="")
    double[] maxs;

    @API(help="Job Key")
    Key job;

    @Override public ExampleModel.ExampleOutput createImpl() {
      ExampleModel.ExampleOutput impl = new ExampleModel.ExampleOutput();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public ExampleModelV2Output fillFromImpl( ExampleModel.ExampleOutput impl) {
      BeanUtils.copyProperties(this, impl, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // ExampleModelV2Output


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelBase, using reflection on the type parameters.
  public ExampleV2.ExampleV2Parameters createParametersSchema() { return new ExampleV2.ExampleV2Parameters(); }
  public ExampleModelV2Output createOutputSchema() { return new ExampleModelV2Output(); }

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
