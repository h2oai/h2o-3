package hex.schemas;

import hex.glrm.GLRMModel;
import water.api.*;

public class GLRMModelV3 extends ModelSchema<GLRMModel, GLRMModelV3, GLRMModel.GLRMParameters, GLRMV3.GLRMParametersV3, GLRMModel.GLRMOutput, GLRMModelV3.GLRMModelOutputV3> {
  public static final class GLRMModelOutputV3 extends ModelOutputSchema<GLRMModel.GLRMOutput, GLRMModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help = "Iterations executed")
    public int iterations;

    @API(help = "Objective value")
    public double objective;

    @API(help = "Average change in objective value on final iteration")
    public double avg_change_obj;

    @API(help = "Final step size")
    public double step_size;

    @API(help = "Mapping from lower dimensional k-space to training features")
    public TwoDimTableBase archetypes;

    @API(help = "Singular values of XY matrix")
    public double[] singular_vals;

    @API(help = "Eigenvectors of XY matrix")
    public TwoDimTableBase eigenvectors;

    @API(help = "Frame key name for X matrix")
    public String representation_name;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GLRMV3.GLRMParametersV3 createParametersSchema() { return new GLRMV3.GLRMParametersV3(); }
  public GLRMModelOutputV3 createOutputSchema() { return new GLRMModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public GLRMModel createImpl() {
    GLRMModel.GLRMParameters parms = parameters.createImpl();
    return new GLRMModel( model_id.key(), parms, null );
  }
}
