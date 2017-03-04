package water.automl;

import water.api.AbstractRegister;
import water.api.RequestServer;
import water.automl.api.AutoMLBuilderHandler;
import water.automl.api.AutoMLHandler;
import water.automl.api.LeaderboardsHandler;
import water.util.Log;

public class RegisterRestApi extends AbstractRegister {
  @Override public void register(String relativeResourcePath) {
    RequestServer.registerEndpoint("automl_build",
            "POST /99/AutoMLBuilder", AutoMLBuilderHandler.class, "build",
            "Start an AutoML build process.");

    RequestServer.registerEndpoint("automls",
            "GET /99/AutoML", AutoMLHandler.class, "list",
            "Return all the AutoML objects.");

    RequestServer.registerEndpoint("automl",
            "GET /99/AutoML/{automl_id}", AutoMLHandler.class, "fetch",
            "Fetch the specified AutoML object.");

    RequestServer.registerEndpoint("leaderboards",
            "GET /99/Leaderboards", LeaderboardsHandler.class, "list",
            "Return all the AutoML leaderboards.");

    RequestServer.registerEndpoint("leaderboard",
            "GET /99/Leaderboards/{project}", LeaderboardsHandler.class, "fetch",
            "Return the AutoML leaderboard for the given project.");
  }

  @Override
  public String getName() {
    return "AutoML";
  }
}
