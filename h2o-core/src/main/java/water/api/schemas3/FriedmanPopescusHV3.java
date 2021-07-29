package water.api.schemas3;

import water.Iced;
import water.api.API;

public class FriedmanPopescusHV3 extends RequestSchemaV3<Iced, FriedmanPopescusHV3> {
  
  @API(help="Model id of interest", json = false)
  public KeyV3.ModelKeyV3 model_id;

  @API(help = "Frame the model has been fitted to", required = true)
  public FrameV3 frame;

  @API(help = "Variables of interest", required = true)
  public String[] variables;

  @API(help = "Value of H statistic")
  public double h;
}
