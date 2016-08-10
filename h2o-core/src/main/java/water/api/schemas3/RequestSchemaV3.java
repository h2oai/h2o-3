package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.Schema;

/**
 * Base Schema class for all REST API requests, gathering up common behavior such as the
 */
public class RequestSchemaV3<I extends Iced, S extends RequestSchemaV3<I, S>> extends SchemaV3<I, S> {

  @API(help="Comma-separated list of JSON field paths to exclude from the result, used like: " +
          "\"/3/Frames?_exclude_fields=frames/frame_id/URL,__meta\"", direction=API.Direction.INPUT)
  public String _exclude_fields = "";

  // @API(help="Not yet implemented", direction=API.Direction.INPUT)
  // public String _include_fields = "";
}
