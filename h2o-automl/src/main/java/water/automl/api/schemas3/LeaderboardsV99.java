package water.automl.api.schemas3;

import water.api.API;
import water.api.schemas3.RequestSchemaV3;
import water.automl.api.LeaderboardsHandler;

public class LeaderboardsV99 extends RequestSchemaV3<LeaderboardsHandler.Leaderboards, LeaderboardsV99> {

  // Input fields
  @API(help="Name of project of interest", json=false)
  public String project_name;

  // Output fields
  @API(help="Leaderboards", direction=API.Direction.OUTPUT)
  public LeaderboardV99[] leaderboards;

}
