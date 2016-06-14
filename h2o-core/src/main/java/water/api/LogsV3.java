package water.api;

import water.Iced;

public class LogsV3 extends SchemaV3<Iced, LogsV3> {
  @API(help="Index of node to query ticks for (0-based).  -1 means current node.", required = true, direction = API.Direction.INPUT)
  public int nodeidx;

  @API(help="Which specific log file to read from the log file directory.  If left unspecified, the system chooses a default for you.", direction = API.Direction.INPUT)
  public String name;

  @API(help="Content of log file", direction = API.Direction.OUTPUT)
  public String log;
}
