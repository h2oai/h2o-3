package water.schemas;

import water.*;
import water.api.Inspect;
import water.api.Handler;
import water.util.DocGen.HTML;

public class InspectV1 extends Schema {

  // Input fields
  @API(help="Key to inspect",validation="/*this input is required*/")
  Key key;

  // Output
  @API(help="Class")
  String className;

  @API(help="Output schema for class")
  Schema schema;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  protected transient Value _val; // To avoid a race, cached lookup here
  @Override public InspectV1 fillInto( Handler h ) {
    _val = DKV.get(key);
    if( _val == null ) throw new IllegalArgumentException("Key not found");
    ((Inspect)h)._val = _val;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public InspectV1 fillFrom( Handler h ) {
    className = _val.className();
    schema = ((Inspect)h)._schema;         // Output schema
    schema.fillFrom(h);                    // Recursively fill in schema
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title(className+" "+key);
    return schema.writeHTML_impl(ab);
  }

  //==========================
  // Helper so ParseV2 can link to InspectV1
  public static String link(Key key) { return "Inspect?key="+key; }
}
