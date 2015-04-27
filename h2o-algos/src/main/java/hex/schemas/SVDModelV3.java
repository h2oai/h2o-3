package hex.schemas;

import hex.svd.SVDModel;
import water.api.*;

public class SVDModelV3 extends ModelSchema<SVDModel, SVDModelV3, SVDModel.SVDParameters, SVDV3.SVDParametersV3, SVDModel.SVDOutput, SVDModelV3.SVDModelOutputV3> {
  public static final class SVDModelOutputV3 extends ModelOutputSchema<SVDModel.SVDOutput, SVDModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help = "Right singular vectors")
    public double[][] v;

    @API(help = "Singular values")
    public double[] d;

    @API(help = "Frame key of left singular vectors")
    public KeyV3.FrameKeyV3 u_key;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public SVDV3.SVDParametersV3 createParametersSchema() { return new SVDV3.SVDParametersV3(); }
  public SVDModelOutputV3 createOutputSchema() { return new SVDModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public SVDModel createImpl() {
    SVDModel.SVDParameters parms = parameters.createImpl();
    return new SVDModel( key.key(), parms, null );
  }
}
