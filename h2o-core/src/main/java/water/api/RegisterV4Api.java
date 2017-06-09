package water.api;

/**
 * Master-class for v4 REST APIs
 */
public class RegisterV4Api extends AbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {

    //------------ Metadata: endpoints and schemas ---------------------------------------------------------------------
    context.registerEndpoint("endpoints4",
            "GET /4/endpoints",
            MetadataHandler.class, "listRoutes4",
            "Returns the list of all REST API (v4) endpoints."
    );


    //------------ Rapids ----------------------------------------------------------------------------------------------
    context.registerEndpoint("POST /4/sessions", RapidsHandler.StartSession4.class);

    context.registerEndpoint("endSession4",
            "DELETE /4/sessions/{session_key}",
            RapidsHandler.class, "endSession",
            "Close the Rapids session."
    );


    //------------ Models ----------------------------------------------------------------------------------------------
    context.registerEndpoint("modelsInfo",
            "GET /4/modelsinfo",
            ModelBuildersHandler.class, "modelsInfo",
            "Return basic information about all models available to train."
    );


    //------------ Frames ----------------------------------------------------------------------------------------------
    context.registerEndpoint("POST /4/Frames/$simple", CreateFrameHandler.CreateSimpleFrame.class);


    //------------ Jobs ------------------------------------------------------------------------------------------------
    context.registerEndpoint("GET /4/jobs/{job_id}", JobsHandler.FetchJob.class);
  }

  @Override
  public String getName() {
    return "Core V4";
  }
}
