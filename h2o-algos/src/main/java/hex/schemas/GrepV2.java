package hex.schemas;

import hex.grep.Grep;
import hex.grep.GrepModel;
import water.api.API;
import water.api.ModelParametersSchema;

public class GrepV2 extends ModelBuilderSchema<Grep,GrepV2,GrepV2.GrepParametersV2> {

  public static final class GrepParametersV2 extends ModelParametersSchema<GrepModel.GrepParameters, GrepParametersV2> {
    static public String[] own_fields = new String[] { "regex" };

    // Input fields
    @API(help="regex")  public String regex;
  } // GrepParametersV2
}
