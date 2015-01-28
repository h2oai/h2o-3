package water.api;

import water.util.TwoDimTable;

public class TwoDimTableV1 extends Schema<TwoDimTable, TwoDimTableV1> {
  @API(help="Table Header", direction=API.Direction.OUTPUT)
  public String tableHeader;
  @API(help="Row Headers", direction=API.Direction.OUTPUT)
  public String[] rowHeaders;
  @API(help="Column Headers", direction=API.Direction.OUTPUT)
  public String[] colHeaders;
  @API(help="Column Types", direction=API.Direction.OUTPUT)
  public String[] colTypes;
  @API(help="Column sprintf Format Strings", direction=API.Direction.OUTPUT)
  public String[] colFormats;
  @API(help="Row-Major Matrix", direction=API.Direction.OUTPUT)
  public String[][] cellValues;
}