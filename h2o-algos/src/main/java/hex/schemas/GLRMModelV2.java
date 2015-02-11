package hex.schemas;

import hex.glrm.GLRMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class GLRMModelV2 extends ModelSchema<GLRMModel, GLRMModelV2, GLRMModel.GLRMParameters, GLRMModel.GLRMOutput> {

  public static final class GLRMModelOutputV2 extends ModelOutputSchema<GLRMModel.GLRMOutput, GLRMModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Column names expanded to accommodate categoricals")
    public String[] namesExp;

    @API(help = "Standard deviation of each principal component")
    public double[] sdev;

    @API(help = "Proportion of variance explained by each principal component")
    public double[] propVar;

    @API(help = "Cumulative proportion of variance explained by each principal component")
    public double[] cumVar;

    @API(help = "Principal components (eigenvector) matrix")
    public double[][] eigVec;

    @API(help = "If standardized, mean of each numeric data column")
    public double[] normSub;

    @API(help = "If standardized, one over standard deviation of each numeric data column")
    public double[] normMul;

    @API(help = "Offsets of categorical columns into the sdev vector. The last value is the offset of the first numerical column.")
    public int[] catOffsets;

    @API(help = "Rank of eigenvector matrix")
    public int rank;

    @API(help = "Number of principal components to display")
    public int num_pc;

    @API(help = "Model parameters")
    GLRMV2 parameters;

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
