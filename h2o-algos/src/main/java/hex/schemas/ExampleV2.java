package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class ExampleV2 extends ModelBuilderSchema<Example,ExampleV2,ExampleV2.ExampleParametersV2> {

  public static final class ExampleParametersV2 extends ModelParametersSchema<ExampleModel.ExampleParameters, ExampleParametersV2> {
    public String[] fields() { return new String[] {"max_iters"}; }

    // Input fields
    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    @Override public ExampleParametersV2 fillFromImpl(ExampleModel.ExampleParameters impl) {
      super.fillFromImpl(impl);

      // Add any special handling here!
      return this;
    }

    @Override public ExampleModel.ExampleParameters fillImpl(ExampleModel.ExampleParameters impl) {
      super.fillImpl(impl);

      // Add any special handling here!
      return impl;
    }
  } // ExampleParametersV2


  //==========================
  // Custom adapters go here

  // Return a URL to invoke Example on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Example?training_frame="+fr._key; }
}
