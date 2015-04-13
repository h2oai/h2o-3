package hex.schemas;

import hex.svd.SVDModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class SVDModelV2 extends ModelSchema<SVDModel, SVDModelV2, SVDModel.SVDParameters, SVDV2.SVDParametersV2, SVDModel.SVDOutput, SVDModelV2.SVDModelOutputV2> {
  public static final class SVDModelOutputV2 extends ModelOutputSchema<SVDModel.SVDOutput, SVDModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Model parameters")
    SVDV2.SVDParametersV2 parameters;

    @API(help = "Iterations executed")
    public int iterations;

    @API(help = "Right singular vectors")
    public double[][] v;

    @API(help = "Singular values")
    public double[] singular_vals;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public SVDV2.SVDParametersV2 createParametersSchema() { return new SVDV2.SVDParametersV2(); }
  public SVDModelOutputV2 createOutputSchema() { return new SVDModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public SVDModel createImpl() {
    SVDModel.SVDParameters parms = parameters.createImpl();
    return new SVDModel( key.key(), parms, null );
  }
}
