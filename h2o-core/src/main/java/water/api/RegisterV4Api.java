package water.api;

import static water.api.RequestServer.registerEndpoint;
import ai.h2o.cascade.CascadeHandlers;


/**
 * Master-class for v4 REST APIs
 */
public class RegisterV4Api extends AbstractRegister {

  @Override
  public void register(String relativeResourcePath) {

    //------------ Metadata: endpoints and schemas ---------------------------------------------------------------------
    registerEndpoint("endpoints4",
        "GET /4/endpoints",
        MetadataHandler.class, "listRoutes4",
        "Returns the list of all REST API (v4) endpoints."
    );


    //------------ Rapids ----------------------------------------------------------------------------------------------
    registerEndpoint("POST /4/sessions", CascadeHandlers.StartSession.class);

    registerEndpoint("endSession4",
        "DELETE /4/sessions/{session_key}",
        RapidsHandler.class, "endSession",
        "Close the Rapids session."
    );

    registerEndpoint("POST /4/cascade", CascadeHandlers.Run.class);


    //------------ Models ----------------------------------------------------------------------------------------------
    registerEndpoint("modelsInfo",
        "GET /4/modelsinfo",
        ModelBuildersHandler.class, "modelsInfo",
        "Return basic information about all models available to train."
    );

  }
}
