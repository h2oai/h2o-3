package hex.schemas;

import hex.pipeline.DataTransformer;
import water.api.API;
import water.api.schemas3.SchemaV3;

public class DataTransformerV3<D extends DataTransformer, S extends DataTransformerV3<D, S>> extends SchemaV3<D, S> {

  @API(help="Transformer identifier (must be unique in the pipeline)", direction=API.Direction.OUTPUT)
  public String id;

  @API(help="A short description of this transformer", direction=API.Direction.OUTPUT)
  public String description;

}
