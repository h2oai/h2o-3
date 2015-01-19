package hex.schemas;

import hex.example.ExampleModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class ExampleModelV2 extends ModelSchema<ExampleModel, ExampleModelV2, ExampleModel.ExampleParameters, ExampleModel.ExampleOutput> {

  public static final class ExampleModelOutputV2 extends ModelOutputSchema<ExampleModel.ExampleOutput, ExampleModelOutputV2> {
    // Output fields
    @API(help="Iterations executed") public int iterations;
    @API(help="") public double[] maxs;
  } // ExampleModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public ExampleV2.ExampleParametersV2 createParametersSchema() { return new ExampleV2.ExampleParametersV2(); }
  public ExampleModelOutputV2 createOutputSchema() { return new ExampleModelOutputV2(); }
}
