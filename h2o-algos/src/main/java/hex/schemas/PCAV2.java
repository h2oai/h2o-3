package hex.schemas;

import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class PCAV2 extends ModelBuilderSchema<PCA,PCAV2,PCAV2.PCAParametersV2> {

  public static final class PCAParametersV2 extends ModelParametersSchema<PCAParameters, PCAParametersV2> {
    static public String[] own_fields = new String[] { "max_pc", "tolerance", "standardize"};

    // Input fields
    @API(help = "maximum number of principal components")
    public int max_pc;

    @API(help = "tolerance")
    public double tolerance;

    @API(help = "standardize")
    public boolean standardize;
  }
}
