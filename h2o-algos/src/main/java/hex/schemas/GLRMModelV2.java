package hex.schemas;

import hex.glrm.GLRMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableV1;
import water.util.TwoDimTable;

public class GLRMModelV2 extends ModelSchema<GLRMModel, GLRMModelV2, GLRMModel.GLRMParameters, GLRMV2.GLRMParametersV2, GLRMModel.GLRMOutput, GLRMModelV2.GLRMModelOutputV2> {
  public static final class GLRMModelOutputV2 extends ModelOutputSchema<GLRMModel.GLRMOutput, GLRMModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Model parameters")
    GLRMV2.GLRMParametersV2 parameters;

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
  public GLRMV2.GLRMParametersV2 createParametersSchema() { return new GLRMV2.GLRMParametersV2(); }
  public GLRMModelOutputV2 createOutputSchema() { return new GLRMModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public GLRMModel createImpl() {
    GLRMV2.GLRMParametersV2 p = ((GLRMV2.GLRMParametersV2)this.parameters);
    GLRMModel.GLRMParameters parms = p.createImpl();
    return new GLRMModel( key.key(), parms, null );
  }
}
