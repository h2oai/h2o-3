package water.api;

import water.*;
import water.api.InspectHandler.InspectPojo;
import water.util.DocGen.HTML;

class InspectV1 extends Schema<InspectPojo, InspectV1> {
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

  @Override public InspectPojo createImpl() {
    _val = DKV.get(key);
    if( _val == null ) throw new IllegalArgumentException("Key not found");
    if( off < 0 ) throw new IllegalArgumentException("Offset must not be negative");
    if( len < 0 ) throw new IllegalArgumentException("Length must not be negative");
    InspectPojo i = new InspectPojo(_val, off, len);
    return i;
  }

  // Version&Schema-specific filling from the handler
  @Override public InspectV1 fillFromImpl( InspectPojo i) {
    className = _val.className();
    schema = i._schema; // Output schema
    schema.fillFromImpl(i);                   // Recursively fill in schema
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
