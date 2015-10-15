package hex.schemas;

import hex.glrm.GLRMModel;
import water.api.*;

public class GLRMModelV99 extends ModelSchema<GLRMModel, GLRMModelV99, GLRMModel.GLRMParameters, GLRMV99.GLRMParametersV99, GLRMModel.GLRMOutput, GLRMModelV99.GLRMModelOutputV99> {
  public static final class GLRMModelOutputV99 extends ModelOutputSchema<GLRMModel.GLRMOutput, GLRMModelOutputV99> {
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
  public GLRMV99.GLRMParametersV99 createParametersSchema() { return new GLRMV99.GLRMParametersV99(); }
  public GLRMModelOutputV99 createOutputSchema() { return new GLRMModelOutputV99(); }

  // Version&Schema-specific filling into the impl
  @Override public GLRMModel createImpl() {
    GLRMModel.GLRMParameters parms = parameters.createImpl();
    return new GLRMModel( model_id.key(), parms, null );
  }
}
