package water.api;

import java.util.Arrays;
import water.api.TypeaheadHandler.Typeahead;
import water.util.DocGen.HTML;

class TypeaheadV2 extends Schema<Typeahead,TypeaheadV2> {

  // Input fields
  @API(help="src", required=true)
  String src;

  @API(help="limit")
  int limit;

  // Output fields
  @API(help="matches")
  String matches[];

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  @Override public Typeahead createImpl() {
    Typeahead t = new Typeahead();
    t._src = src;
    t._limit = limit<=0 ? 1000 : limit;
    return t;
  }

  // Version&Schema-specific filling from the impl
  @Override public TypeaheadV2 fillFromImpl(Typeahead t) {
    matches = t._matches;
    return this;
  }
}
