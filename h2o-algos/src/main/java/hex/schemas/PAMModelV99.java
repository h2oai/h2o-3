package hex.schemas;

import hex.pam.PAMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class PAMModelV99 extends ModelSchema<PAMModel, PAMModelV99, PAMModel.PAMParameters, PAMV99.PAMParametersV99, PAMModel.PAMOutput, PAMModelV99.PAMModelOutputV99> {

  public static final class PAMModelOutputV99 extends ModelOutputSchema<PAMModel.PAMOutput, PAMModelOutputV99> {
    @API(help="Cluster medoids") public double[/*k*/][/*p*/] medoids;
  }


  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public PAMV99.PAMParametersV99 createParametersSchema() { return new PAMV99.PAMParametersV99(); }
  public PAMModelOutputV99 createOutputSchema() { return new PAMModelOutputV99(); }


  // Version&Schema-specific filling into the impl
  @Override public PAMModel createImpl() {
    PAMModel.PAMParameters parms = parameters.createImpl();
    return new PAMModel( model_id.key(), parms, null );
  }
}
