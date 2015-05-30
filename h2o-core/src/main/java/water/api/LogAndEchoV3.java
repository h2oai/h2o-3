package water.api;

import water.Iced;

public class LogAndEchoV3 extends RequestSchema<Iced, LogAndEchoV3> {
  //Input
  @API(help="Message to be Logged and Echoed")
  String message;
}
