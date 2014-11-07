package water.api;

public class LogAndEchoV1 extends Schema<LogAndEchoHandler.LogAndEcho,LogAndEchoV1> {
  //Input
  @API(help="Message to be Logged and Echoed")
  String message;
  @Override public LogAndEchoV1 fillFromImpl(LogAndEchoHandler.LogAndEcho u ) { message = u._message; return this; }
}