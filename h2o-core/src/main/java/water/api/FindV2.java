package water.api;

import water.Key;
import water.api.FindHandler.FindPojo;
import water.fvec.Frame;
import water.fvec.Vec;

class FindV2 extends Schema<FindPojo,FindV2> {

  // Input fields
  @API(help="Frame to search",required=true)
  Key key;

  @API(help="Column, or null for all")
  String column;

  @API(help="Starting row for search",required=true)
  long row;

  @API(help="Value to search for; leave blank for a search for missing values")
  String match;

  // Output
  @API(help="previous row with matching value, or -1", direction=API.Direction.OUTPUT)
  long prev;

  @API(help="next row with matching value, or -1", direction=API.Direction.OUTPUT)
  long next;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public FindPojo fillImpl(FindPojo f) {
    super.fillImpl(f);

    // Peel out an optional column; restrict to this column
    if( column != null ) {
      Vec vec = f._fr.vec(column);
      if( vec==null ) throw new IllegalArgumentException("Column "+column+" not found in frame "+key);
      f._fr = new Frame(new String[]{column}, new Vec[]{vec});
    }

    return f;
  }

  //==========================
  // Helper so InspectV2 can link to FindV2
  static String link(Key key, String column, long row, String match ) {
    return "/2/Find?key="+key+(column==null?"":"&column="+column)+"&row="+row+(match==null?"":"&match="+match);
  }
}
