package hex.schemas;

import hex.pca.PCA;
import hex.pca.PCAModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class PCAModelV2 extends ModelSchema<PCAModel, PCAModelV2, PCAModel.PCAParameters, PCAModel.PCAOutput> {

  public static final class PCAModelOutputV2 extends ModelOutputSchema<PCAModel.PCAOutput, PCAModelOutputV2> {
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
    PCAV2 parameters;

  } // PCAModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public PCAV2.PCAParametersV2 createParametersSchema() { return new PCAV2.PCAParametersV2(); }
  public PCAModelOutputV2 createOutputSchema() { return new PCAModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public PCAModel createImpl() {
    PCAV2.PCAParametersV2 p = ((PCAV2.PCAParametersV2)this.parameters);
    PCAModel.PCAParameters parms = p.createImpl();
    return new PCAModel( key.key(), parms, null );
  }
}
