package hex.schemas;

import hex.example.ExampleModel;
import water.H2O;
import water.Key;
import water.api.*;

public class ExampleModelV2 extends Schema {

  // Input fields
  @API(help="Example Model to inspect",required=true)
  Key key;

  @API(help="Iterations executed")
  public double iters;


  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ExampleModelV2 fillInto( Handler h ) {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public ExampleModelV2 fillFrom( Handler h ) {
    if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    InspectHandler ih = (InspectHandler)h;
    ExampleModel kmm = ih._val.get();
    iters = kmm._iters;
    return this;
  }
}
