package water.api;

import water.Iced;
import water.Key;

class FindV3 extends SchemaV3<Iced, FindV3> {

  // Input fields
  @API(help="Frame to search",required=true)
  FrameV3 key;

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
  // Helper so InspectV2 can link to FindV2
  static String link(Key key, String column, long row, String match ) {
    return "/2/Find?key="+key+(column==null?"":"&column="+column)+"&row="+row+(match==null?"":"&match="+match);
  }
}
