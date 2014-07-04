package hex.schemas;

import hex.example.ExampleModel;
import water.H2O;
import water.Key;
import water.Model;
import water.api.*;

public class ExampleModelV2 extends ModelBase<Model, ExampleModelV2> {

  // Input fields
  @API(help="Example Model to inspect",required=true)
  Key key;

  @API(help="Iterations executed")
  public double iters;


  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public Model createImpl() {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public ExampleModelV2 fillFromImpl( Model m ) {
    // TODO: if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // TODO: InspectHandler ih = (InspectHandler)h;
    // TODO: ExampleModel kmm = ih._val.get();
    // TODO: iters = kmm._iters;
    return this;
  }
}
