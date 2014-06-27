package water.api;

import water.DKV;
import water.Key;
import water.Value;
import water.fvec.Frame;
import water.fvec.Vec;

class FindV2 extends Schema<FindHandler,FindV2> {

  // Input fields
  @API(help="Frame to search",required=true)
  Key key;

  @API(help="Column, or null for all",dependsOn={"key"})
  String column;

  @API(help="Starting row for search",required=true)
  long row;

  @API(help="Value to search for; leave blank for a search for missing values")
  String match;

  // Output
  @API(help="previous row with matching value, or -1")
  long prev;

  @API(help="next row with matching value, or -1")
  long next;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected FindV2 fillInto( FindHandler h ) {
    // Peel out the Frame from the Key
    Value val = DKV.get(key);
    if( val == null ) throw new IllegalArgumentException("Key not found");
    if( !val.isFrame() ) throw new IllegalArgumentException("Not a Frame");
    Frame fr = val.get();

    // Peel out an optional column; restrict to this column
    if( column != null ) {
      Vec vec = fr.vec(column);
      if( vec==null ) throw new IllegalArgumentException("Column "+column+" not found in frame "+key);
      fr = new Frame(new String[]{column}, new Vec[]{vec});
    }
    
    h._fr = fr;
    h._row = row;
    h._val = match;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected FindV2 fillFrom( FindHandler h ) {
    prev = h._prev;
    next = h._next;
    return this;
  }

  //==========================
  // Helper so InspectV2 can link to FindV2
  static String link(Key key, String column, long row, String match ) { 
    return "/2/Find?key="+key+(column==null?"":"&column="+column)+"&row="+row+(match==null?"":"&match="+match);
  }
}
