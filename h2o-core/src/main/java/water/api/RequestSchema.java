package water.api;

import water.Iced;

/**
 * Base Schema class for all REST API requests, gathering up common behavior such as the
 * __exclude_fields/__include_fields query params.
<p>
 */
public class RequestSchema<I extends Iced, S extends Schema<I,S>> extends Schema<I, S> {
  @API(help="Comma-separated list of JSON field paths to exclude from the result, used like: \"/3/Frames?_exclude_fields=frames/frame_id/URL,__meta\"", direction=API.Direction.INPUT)
  public String _exclude_fields = "";

  // @API(help="Not yet implemented", direction=API.Direction.INPUT)
  // public String _include_fields = "";
}
