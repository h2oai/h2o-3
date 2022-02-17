package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class ExampleV3 extends ModelBuilderSchema<Example,ExampleV3,ExampleV3.ExampleParametersV3> {

  public static final class ExampleParametersV3 extends ModelParametersSchemaV3<ExampleModel.ExampleParameters, ExampleParametersV3> {
    static public String[] fields = new String[] {
            "model_id",
            "training_frame",

            // model specific
            "max_iterations",
    };

    // Input fields
    @API(help="Maximum training iterations.")  public int max_iterations;
  } // ExampleParametersV3
}
