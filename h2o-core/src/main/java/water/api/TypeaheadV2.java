package water.api;

import java.util.Arrays;
import water.util.DocGen.HTML;

class TypeaheadV2 extends Schema<TypeaheadHandler,TypeaheadV2> {

  // Input fields
  @API(help="src", required=true)
  String src;

  @API(help="limit")
  int limit;

  // Output fields
  @API(help="matches")
  String matches[];

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected TypeaheadV2 fillInto( TypeaheadHandler h ) {
    h._src = src;
    h._limit = limit<=0 ? 1000 : limit;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected TypeaheadV2 fillFrom( TypeaheadHandler h ) {
    matches = h._matches;
    return this;
  }
}
