package water.automl;

import water.api.AbstractRegister;
import water.api.RequestServer;
import water.automl.api.AutoMLBuilderHandler;
import water.automl.api.AutoMLHandler;
import water.util.Log;

public class Register extends AbstractRegister{
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    // H2O.register("POST /3/AutoMLBuilder", AutoMLBuilderHandler.class, "POST", "automl", "automatically build models");
    // H2O.register("GET /3/AutoML/{automl_id}", AutoMLHandler.class,"refresh", "GET", "refresh the model key");
    // H2O.register("GET /3/AutoMLJSONSchemaHandler", AutoMLJSONSchemaHandler.class, "GET", "getJSONSchema", "Get the json schema for the AutoML input fields.");

    RequestServer.registerEndpoint("automl_build",
            "POST /3/AutoMLBuilder", AutoMLBuilderHandler.class, "build",
            "Start an AutoML build process.");

    RequestServer.registerEndpoint("fetch",
            "GET /3/AutoML/{automl_id}", AutoMLHandler.class, "fetch",
            "Fetch the specified AutoML object.");
/*
    RequestServer.registerEndpoint("automl_schema",

            "GET /3/AutoMLJSONSchemaHandler", AutoMLJSONSchemaHandler.class, "getJSONSchema",
            "Get the json schema for the AutoML input fields.");
*/
    Log.info("H2O AutoML extensions enabled.");

  }
}
