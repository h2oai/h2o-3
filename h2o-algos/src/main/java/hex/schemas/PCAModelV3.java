package hex.schemas;

import hex.pca.PCAModel;
import water.api.*;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;

public class PCAModelV3 extends ModelSchemaV3<PCAModel, PCAModelV3, PCAModel.PCAParameters, PCAV3.PCAParametersV3, PCAModel.PCAOutput, PCAModelV3.PCAModelOutputV3> {
  public static final class PCAModelOutputV3 extends ModelOutputSchemaV3<PCAModel.PCAOutput, PCAModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help = "Standard deviation and importance of each principal component")
    public TwoDimTableV3 importance;

    @API(help = "Principal components matrix")
    public TwoDimTableV3 eigenvectors;

    @API(help = "Final value of GLRM squared loss function")
    public double objective;
  }

  // TODO: I think we can implement the following two in ModelSchemaV3, using reflection on the type parameters.
  public PCAV3.PCAParametersV3 createParametersSchema() { return new PCAV3.PCAParametersV3(); }
  public PCAModelOutputV3 createOutputSchema() { return new PCAModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public PCAModel createImpl() {
    PCAModel.PCAParameters parms = parameters.createImpl();
    return new PCAModel( model_id.key(), parms, null );
  }
}
