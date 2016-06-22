package water.api.schemas3;

import water.Iced;
import water.Key;
import water.api.API;

public class FindV3 extends SchemaV3<Iced, FindV3> {

  // Input fields
  @API(help="Frame to search",required=true)
  public FrameV3 key;

  @API(help="Column, or null for all")
  public String column;

  @API(help="Starting row for search",required=true)
  public long row;

  @API(help="Value to search for; leave blank for a search for missing values")
  public String match;

  // Output
  @API(help="previous row with matching value, or -1", direction=API.Direction.OUTPUT)
  public long prev;

  @API(help="next row with matching value, or -1", direction=API.Direction.OUTPUT)
  public long next;

  //==========================
  // Helper so InspectV2 can link to FindV2
  static String link(Key key, String column, long row, String match ) {
    return "/2/Find?key="+key+(column==null?"":"&column="+column)+"&row="+row+(match==null?"":"&match="+match);
  }
}
