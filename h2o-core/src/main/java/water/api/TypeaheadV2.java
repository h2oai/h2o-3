package water.api;

import water.Iced;

class TypeaheadV2 extends Schema<Iced,TypeaheadV2> {

  // Input fields
  @API(help="training_frame", required=true)
  String src;

  @API(help="limit")
  int limit;

  // Output fields
  @API(help="matches", direction=API.Direction.OUTPUT)
  String matches[];
}
