package water.api;

import water.api.TypeaheadHandler.Typeahead;

class TypeaheadV2 extends Schema<Typeahead,TypeaheadV2> {

  // Input fields
  @API(help="training_frame", required=true)
  String src;

  @API(help="limit")
  int limit;

  // Output fields
  @API(help="matches", direction=API.Direction.OUTPUT)
  String matches[];

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  @Override public Typeahead fillImpl(Typeahead t) {
    limit = limit <=0 ? 1000 : limit;
    super.fillImpl(t);
    return t;
  }
}
