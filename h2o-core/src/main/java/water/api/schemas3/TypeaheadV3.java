package water.api.schemas3;

import water.Iced;
import water.api.API;

public class TypeaheadV3 extends SchemaV3<Iced,TypeaheadV3> {

  // Input fields
  @API(help="training_frame", required=true)
  public String src;

  @API(help="limit")
  public int limit;

  // Output fields
  @API(help="matches", direction=API.Direction.OUTPUT)
  public String matches[];
}
