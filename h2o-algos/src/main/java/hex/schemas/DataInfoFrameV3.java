package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.KeyV3;
import water.api.SchemaV3;

public class DataInfoFrameV3 extends SchemaV3<Iced,DataInfoFrameV3> {
  @API(help="input frame")                                  public KeyV3.FrameKeyV3 frame;
  @API(help="interactions")                                 public String[] interactions;
  @API(help="use all factor levels")                        public boolean use_all;
  @API(help="standardize")                                  public boolean standardize;
  @API(help="interactions only returned")                   public boolean interactions_only;
  @API(help="output frame", direction=API.Direction.OUTPUT) public KeyV3.FrameKeyV3 result;
}
