package water.api.schemas3;

import water.Iced;
import water.api.API;

public class LogAndEchoV3 extends SchemaV3<Iced, LogAndEchoV3> {
  //Input
  @API(help="Message to be Logged and Echoed")
  public String message;
}
