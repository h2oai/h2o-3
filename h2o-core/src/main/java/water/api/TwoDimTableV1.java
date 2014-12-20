package water.api;

import water.util.TwoDimTable;

public class TwoDimTableV1 extends Schema<TwoDimTable, TwoDimTableV1> {
  @API(help="description", direction=API.Direction.OUTPUT)
  String description;
  @API(help="column names", direction=API.Direction.OUTPUT)
  String[] colNames;
  @API(help="column printf format strings", direction=API.Direction.OUTPUT)
  String[] colFormatStrings; //optional
  @API(help="row headers", direction=API.Direction.OUTPUT)
  String[] rowHeaders;
  @API(help="matrix of string values", direction=API.Direction.OUTPUT)
  String[][] strings;
  @API(help="matrix of numeric values", direction=API.Direction.OUTPUT)
  double[][] doubles;
}
