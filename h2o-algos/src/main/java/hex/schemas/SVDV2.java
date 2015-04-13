package hex.schemas;

import hex.svd.SVD;
import hex.svd.SVDModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class SVDV2 extends ModelBuilderSchema<SVD,SVDV2,SVDV2.SVDParametersV2> {

  public static final class SVDParametersV2 extends ModelParametersSchema<SVDModel.SVDParameters, SVDParametersV2> {
    static public String[] own_fields = new String[] { "k" };

    @API(help = "Number of singular vectors")
    public int k;
  }
}