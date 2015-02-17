package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class ExampleV2 extends ModelBuilderSchema<Example,ExampleV2,ExampleV2.ExampleParametersV2> {

  public static final class ExampleParametersV2 extends ModelParametersSchema<ExampleModel.ExampleParameters, ExampleParametersV2> {
    static public String[] own_fields = new String[] { "training_frame","ignored_columns","max_iterations"};

    // Input fields
    @API(help="Maximum training iterations.")  public int max_iterations;
  } // ExampleParametersV2
}
