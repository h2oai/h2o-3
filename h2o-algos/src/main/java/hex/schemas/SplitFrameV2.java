package hex.schemas;

import hex.splitframe.SplitFrame;
import hex.splitframe.SplitFrameModel;
import water.Key;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class SplitFrameV2 extends ModelBuilderSchema<SplitFrame,SplitFrameV2,SplitFrameV2.SplitFrameParametersV2> {

  public static final class SplitFrameParametersV2 extends ModelParametersSchema<SplitFrameModel.SplitFrameParameters, SplitFrameParametersV2> {
    static public String[] own_fields = new String[] {"ratios", "destKeys"};

    @API(help="Split ratios - resulting number of split is ratios.length+1")
    public double[] ratios;

    @API(help="Destination keys for each output frame split.")
    public Key[] destKeys;
  }


  //==========================
  // Custom adapters go here

  // Return a URL to invoke SplitFrame on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/SplitFrame?training_frame="+fr._key; }
}
