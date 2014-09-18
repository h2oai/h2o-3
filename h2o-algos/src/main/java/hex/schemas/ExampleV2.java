package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.api.*;
import water.fvec.Frame;
import water.util.PojoUtils;

public class ExampleV2 extends ModelBuilderSchema<Example,ExampleV2,ExampleV2.ExampleParametersV2> {

  public static final class ExampleParametersV2 extends ModelParametersSchema<ExampleModel.ExampleParameters, ExampleParametersV2> {
    public String[] fields() { return new String[] {"max_iters"}; }

    // Input fields
    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    // TODO: refactor into ModelParametersSchema
    @Override public ExampleModel.ExampleParameters createImpl() {
      ExampleModel.ExampleParameters impl = new ExampleModel.ExampleParameters();
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }
  } // ExampleParametersV2


  //==========================
  // Custom adapters go here

  @Override public ExampleParametersV2 createParametersSchema() { return new ExampleParametersV2(); }

  // Version&Schema-specific filling into the impl
  @Override public Example createImpl() {
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters == 0 ) parameters.max_iters = 1000; // Default is 1000 max_iters

    return new Example(parameters.createImpl());
  }

  // Return a URL to invoke Example on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Example?training_frame="+fr._key; }
}
