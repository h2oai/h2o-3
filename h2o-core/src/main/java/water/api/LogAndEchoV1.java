package water.api;

import water.Iced;

public class LogAndEchoV1 extends Schema<Iced, LogAndEchoV1> {
  //Input
  @API(help="Message to be Logged and Echoed")
  String message;
}