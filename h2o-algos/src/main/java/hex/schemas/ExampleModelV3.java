package hex.schemas;

import hex.example.ExampleModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class ExampleModelV3 extends ModelSchemaV3<ExampleModel, ExampleModelV3, ExampleModel.ExampleParameters, ExampleV3.ExampleParametersV3, ExampleModel.ExampleOutput, ExampleModelV3.ExampleModelOutputV3> {

  public static final class ExampleModelOutputV3 extends ModelOutputSchemaV3<ExampleModel.ExampleOutput, ExampleModelOutputV3> {
    // Output fields
    @API(help="Iterations executed") public int iterations;
    @API(help="") public double[] maxs;
  } // ExampleModelOutputV2


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchemaV3, using reflection on the type parameters.
  public ExampleV3.ExampleParametersV3 createParametersSchema() { return new ExampleV3.ExampleParametersV3(); }
  public ExampleModelOutputV3 createOutputSchema() { return new ExampleModelOutputV3(); }
}
