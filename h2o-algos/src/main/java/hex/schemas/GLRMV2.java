package hex.schemas;

import hex.glrm.GLRM;
import hex.glrm.GLRMModel.GLRMParameters;
import water.api.API;
import water.api.ModelParametersSchema;

public class GLRMV2 extends ModelBuilderSchema<GLRM,GLRMV2,GLRMV2.GLRMParametersV2> {

  public static final class GLRMParametersV2 extends ModelParametersSchema<GLRMParameters, GLRMParametersV2> {
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
