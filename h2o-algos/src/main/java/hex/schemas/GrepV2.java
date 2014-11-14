package hex.schemas;

import hex.grep.Grep;
import hex.grep.GrepModel;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class GrepV2 extends ModelBuilderSchema<Grep,GrepV2,GrepV2.GrepParametersV2> {

  public static final class GrepParametersV2 extends ModelParametersSchema<GrepModel.GrepParameters, GrepParametersV2> {
    public String[] fields() { return new String[] {"training_frame","regex"}; }

    // Input fields
    @API(help="regex")  public String regex;
  } // GrepParametersV2


  //==========================
  // Custom adapters go here

  // Return a URL to invoke Grep on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Grep?training_frame="+fr._key; }
}
