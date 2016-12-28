package water.api;

import ai.h2o.cascade.CascadeHandlers;

import static water.api.RequestServer.registerEndpoint;


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


    //------------ Cascade ---------------------------------------------------------------------------------------------
    registerEndpoint("POST /4/sessions", CascadeHandlers.StartSession.class);

    registerEndpoint("DELETE /4/sessions/{session_id}", CascadeHandlers.CloseSession.class);

    registerEndpoint("POST /4/cascade", CascadeHandlers.Run.class);


    //------------ Models ----------------------------------------------------------------------------------------------
    registerEndpoint("modelsInfo",
        "GET /4/modelsinfo",
        ModelBuildersHandler.class, "modelsInfo",
        "Return basic information about all models available to train."
    );

  }
}
