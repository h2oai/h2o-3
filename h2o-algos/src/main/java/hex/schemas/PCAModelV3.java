package hex.schemas;

import hex.pca.PCAModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;

public class PCAModelV3 extends ModelSchema<PCAModel, PCAModelV3, PCAModel.PCAParameters, PCAV3.PCAParametersV3, PCAModel.PCAOutput, PCAModelV3.PCAModelOutputV3> {
  public static final class PCAModelOutputV3 extends ModelOutputSchema<PCAModel.PCAOutput, PCAModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help = "Iterations executed")
    public int iterations;

    @API(help = "Mapping from training data to lower dimensional k-space")
    public double[][] archetypes;

    @API(help = "Standard deviation of each principal component")
    public double[] std_deviation;

    @API(help = "Principal components matrix")
    public TwoDimTableBase eigenvectors;

    @API(help = "Importance of each principal component")
    public TwoDimTableBase pc_importance;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public PCAV3.PCAParametersV3 createParametersSchema() { return new PCAV3.PCAParametersV3(); }
  public PCAModelOutputV3 createOutputSchema() { return new PCAModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public PCAModel createImpl() {
    PCAModel.PCAParameters parms = parameters.createImpl();
    return new PCAModel( model_id.key(), parms, null );
  }
}
