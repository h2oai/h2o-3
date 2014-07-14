package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.Key;
import water.api.*;
import water.fvec.Frame;
import water.util.BeanUtils;
import water.util.DocGen.HTML;

public class ExampleV2 extends ModelBuilderSchema<Example,ExampleV2,ExampleV2.ExampleV2Parameters> {

  public static final class ExampleV2Parameters extends ModelParametersSchema<ExampleModel.ExampleParameters, ExampleV2Parameters> {
    // Input fields
    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    // TODO: refactor into ModelParametersSchema
    @Override public ExampleModel.ExampleParameters createImpl() {
      ExampleModel.ExampleParameters impl = new ExampleModel.ExampleParameters();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }
  } // ExampleV2Parameters


  //==========================
  // Custom adapters go here

  @Override public ExampleV2Parameters createParametersSchema() { return new ExampleV2Parameters(); }

  // Version&Schema-specific filling into the impl
  @Override public Example createImpl() {
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters == 0 ) parameters.max_iters = 1000; // Default is 1000 max_iters

    Example e = new Example(parameters.createImpl());
    return e;
  }

  // Return a URL to invoke Example on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Example?src="+fr._key; }
}
