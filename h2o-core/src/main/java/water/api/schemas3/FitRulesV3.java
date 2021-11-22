package water.api.schemas3;

import water.Iced;
import water.api.API;

public class FitRulesV3 extends RequestSchemaV3<Iced, FitRulesV3> {
  
  @API(help="Model id of interest", json = false)
  public KeyV3.ModelKeyV3 model_id;

  @API(help = "Frame on which rule validity is to be evaluated", required = true)
  public FrameV3 frame;
//TODO docs
  @API(help = "String array of rule ids to be evaluated against the frame", required = true)
  public String[] rule_ids;

//  @API(help = "Frame the model has been fitted to", required = true)
//  public FrameV3 result;

  //output
  @API(help="Output of the assembly line.", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 result;
}
