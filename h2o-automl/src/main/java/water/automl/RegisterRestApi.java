package water.automl;

import water.api.AbstractRegister;
import water.api.RestApiContext;
import water.automl.api.AutoMLBuilderHandler;
import water.automl.api.AutoMLHandler;
import water.automl.api.LeaderboardsHandler;

public class RegisterRestApi extends AbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    context.registerEndpoint("automl_build",
            "POST /99/AutoMLBuilder", AutoMLBuilderHandler.class, "build",
            "Start an AutoML build process.");

    context.registerEndpoint("automl",
            "GET /99/AutoML/{automl_id}", AutoMLHandler.class, "fetch",
            "Fetch the specified AutoML object.");

    context.registerEndpoint("leaderboards",
            "GET /99/Leaderboards", LeaderboardsHandler.class, "list",
            "Return all the AutoML leaderboards.");

    context.registerEndpoint("leaderboard",
            "GET /99/Leaderboards/{project_name}", LeaderboardsHandler.class, "fetch",
            "Return the AutoML leaderboard for the given project.");
    context.registerEndpoint("exportAutoML",
            "GET /99/AutoML.bin/{automl_id}", AutoMLHandler.class, "exportBinaryAutoML",
            "Export AutoML as a binary object.");
    context.registerEndpoint("fetchAutoML",
            "GET /99/AutoML.fetch.bin/{automl_id}", AutoMLHandler.class, "fetchBinaryAutoML",
            "Fetch AutoML as a binary object.");
    context.registerEndpoint("importAutoML",
            "POST /99/AutoML.bin", AutoMLHandler.class, "importBinaryAutoML",
            "Import AutoML as a binary object.");
    context.registerEndpoint("uploadAutoML",
            "POST /99/AutoML.upload.bin", AutoMLHandler.class, "uploadBinaryAutoML",
            "Upload AutoML as a binary object.");
  }

  @Override
  public String getName() {
    return "AutoML";
  }
}
