package water.api;

/**
 * Master-class for v4 REST APIs
 */
public class RegisterV4Api extends AbstractRegister {

  @Override
  public void register(String relativeResourcePath) {

    //------------ Metadata: endpoints and schemas ---------------------------------------------------------------------
    RequestServer.register("endpoints4",
        "GET /4/endpoints",
        MetadataHandler.class, "listRoutes4",
        "Returns the list of all REST API (v4) endpoints."
    );

  }
}
