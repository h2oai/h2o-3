package hex.api;

import water.api.AbstractRegister;
import water.api.RestApiContext;

public class MojoPipelineApiRegister extends AbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    context.registerEndpoint(
      "_assembly_fetch_mojo_pipeline",
      "GET /99/Assembly.fetch_mojo_pipeline/{assembly_id}/{file_name}",
      AssemblyToMojoPipelineExportHandler.class, 
      "fetchMojoPipeline",
      "Generate a MOJO 2 pipeline artifact from the Assembly");
  }

  @Override
  public String getName() {
    return "Mojo 2 pipeline extensions";
  }
}
