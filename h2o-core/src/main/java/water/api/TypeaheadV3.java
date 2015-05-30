package water.api;

import water.Iced;

public class TypeaheadV3 extends RequestSchema<Iced,TypeaheadV3> {

  // Input fields
  @API(help="training_frame", required=true)
  String src;

  @API(help="limit")
  int limit;

  // Output fields
  @API(help="matches", direction=API.Direction.OUTPUT)
  String matches[];
}
