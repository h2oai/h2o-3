package hex.schemas;

import hex.svd.SVDModel;
import water.api.*;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class SVDModelV99 extends ModelSchemaV3<SVDModel, SVDModelV99, SVDModel.SVDParameters, SVDV99.SVDParametersV99, SVDModel.SVDOutput, SVDModelV99.SVDModelOutputV99> {
  public static final class SVDModelOutputV99 extends ModelOutputSchemaV3<SVDModel.SVDOutput, SVDModelOutputV99> {
    // Output fields; input fields are in the parameters list
    @API(help = "Frame key of right singular vectors")
    public KeyV3.FrameKeyV3 v_key;

    @API(help = "Singular values")
    public double[] d;

    @API(help = "Frame key of left singular vectors")
    public KeyV3.FrameKeyV3 u_key;
  }

  // TODO: I think we can implement the following two in ModelSchemaV3, using reflection on the type parameters.
  public SVDV99.SVDParametersV99 createParametersSchema() { return new SVDV99.SVDParametersV99(); }
  public SVDModelOutputV99 createOutputSchema() { return new SVDModelOutputV99(); }

  // Version&Schema-specific filling into the impl
  @Override public SVDModel createImpl() {
    SVDModel.SVDParameters parms = parameters.createImpl();
    return new SVDModel( model_id.key(), parms, null );
  }
}
