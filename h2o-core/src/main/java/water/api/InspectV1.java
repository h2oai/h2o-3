package water.api;

import water.*;
import water.util.DocGen.HTML;

class InspectV1 extends Schema<InspectHandler,InspectV1> {

  // Input fields
  @API(help="Key to inspect",required=true)
  Key key;

  @API(help="Offset, used to page through large objects",direction=API.Direction.INPUT)
  long off;

  @API(help="Length, used to page through large objects",direction=API.Direction.INOUT)
  int len;

  // Output
  @API(help="Class")
  String className;

  @API(help="Output schema for class")
  Schema schema;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  private transient Value _val; // To avoid a race, cached lookup here
  @Override protected InspectV1 fillInto( InspectHandler h ) {
    _val = DKV.get(key);
    if( _val == null ) throw new IllegalArgumentException("Key not found");
    h._val = _val;
    if( off < 0 ) throw new IllegalArgumentException("Offset must not be negative");
    h._off = off;
    if( len < 0 ) throw new IllegalArgumentException("Length must not be negative");
    h._len = len;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected InspectV1 fillFrom( InspectHandler h ) {
    className = _val.className();
    schema = h._schema; // Output schema
    schema.fillFrom(h);                   // Recursively fill in schema
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title(className+" "+key);
    return schema.writeHTML(ab);
  }

  //==========================
  // Helper so ParseV2 can link to InspectV1
  static String link(Key key) { return "Inspect?key="+key; }
}
