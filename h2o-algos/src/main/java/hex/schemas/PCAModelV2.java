package hex.schemas;

import hex.pca.PCAModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableV1;

public class PCAModelV2 extends ModelSchema<PCAModel, PCAModelV2, PCAModel.PCAParameters, PCAV2.PCAParametersV2, PCAModel.PCAOutput, PCAModelV2.PCAModelOutputV2> {
  public static final class PCAModelOutputV2 extends ModelOutputSchema<PCAModel.PCAOutput, PCAModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Model parameters")
    PCAV2.PCAParametersV2 parameters;

    @API(help = "Iterations executed")
    public int iterations;

    @API(help = "Mapping from training data to lower dimensional k-space")
    public double[][] archetypes;

    @API(help = "Standard deviation of each principal component")
    public double[] std_deviation;

    @API(help = "Principal components matrix")
    public TwoDimTableV1 eigenvectors;

    @API(help = "Importance of each principal component")
    public TwoDimTableV1 pc_importance;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public PCAV2.PCAParametersV2 createParametersSchema() { return new PCAV2.PCAParametersV2(); }
  public PCAModelOutputV2 createOutputSchema() { return new PCAModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public PCAModel createImpl() {
    PCAModel.PCAParameters parms = parameters.createImpl();
    return new PCAModel( key.key(), parms, null );
  }
}
