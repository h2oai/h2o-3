package water.api;

/**
 * Master-class for v4 REST APIs
 */
public class RegisterV4Api extends AbstractRegister {

  @Override
  public void register(String relativeResourcePath) {

    //------------ Metadata: endpoints and schemas ---------------------------------------------------------------------
    RequestServer.registerEndpoint("endpoints4",
        "GET /4/endpoints",
        MetadataHandler.class, "listRoutes4",
        "Returns the list of all REST API (v4) endpoints."
    );


    //------------ Rapids ----------------------------------------------------------------------------------------------
    RequestServer.registerEndpoint("newSession4",
        "POST /4/sessions",
        RapidsHandler.class, "startSession",
        "Start a new Rapids session, and return the session id."
    );

    RequestServer.registerEndpoint("endSession4",
        "DELETE /4/sessions/{session_key}",
        RapidsHandler.class, "endSession",
        "Close the Rapids session."
    );
  }
}
