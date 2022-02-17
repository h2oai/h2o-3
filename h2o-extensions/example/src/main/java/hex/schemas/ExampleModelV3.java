package hex.schemas;

import hex.example.ExampleModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class ExampleModelV3 extends ModelSchemaV3<ExampleModel, ExampleModelV3, ExampleModel.ExampleParameters, ExampleV3.ExampleParametersV3, ExampleModel.ExampleOutput, ExampleModelV3.ExampleModelOutputV3> {

  public static final class ExampleModelOutputV3 extends ModelOutputSchemaV3<ExampleModel.ExampleOutput, ExampleModelOutputV3> {
    // Output fields
    @API(help="Iterations executed")
    public int iterations;

    @API(help="Maximum value per column") public double[] maxs;
  } // ExampleModelOutputV3


  //==========================
  // Custom adapters go here

  public ExampleV3.ExampleParametersV3 createParametersSchema() { return new ExampleV3.ExampleParametersV3(); }
  public ExampleModelOutputV3 createOutputSchema() { return new ExampleModelOutputV3(); }
}
