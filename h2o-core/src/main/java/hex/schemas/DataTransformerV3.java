package hex.schemas;

import hex.pipeline.DataTransformer;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class DataTransformerV3<D extends DataTransformer, S extends DataTransformerV3<D, S>> extends SchemaV3<D, S> {
  
  @API(help="Transformer key", direction=API.Direction.INOUT)
  public KeyV3.DataTransformerKeyV3 key;

  @API(help="Transformer name (must be unique in the pipeline)", direction=API.Direction.OUTPUT)
  public String name;

  @API(help="A short description of this transformer", direction=API.Direction.OUTPUT)
  public String description;

  @Override
  public D createImpl() {
    // later we can create more specific transformers if we need to expose the internals.
    return (D) new ClientDataTransformer();
  }
}
