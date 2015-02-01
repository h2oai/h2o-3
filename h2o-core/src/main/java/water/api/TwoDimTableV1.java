package water.api;

import water.H2O;
import water.Iced;
import water.util.TwoDimTable;

/**
 * Client-facing Schema of a TwoDimTable
 * Notes:
 * 1) We embed the rowHeaders into the table, extending it by 1 column
 * 2) We store all the data in column-major order
 * 3) We store all the data in String format
 *
 */
public class TwoDimTableV1 extends Schema<TwoDimTable, TwoDimTableV1> {
  static class ColumnSpecs extends Iced {
    String name;
    String type;
    String format;
    String description;
  }
  public static class ColumnSpecsV1 extends Schema<ColumnSpecs, ColumnSpecsV1> {
    @API(help="Column Name", direction=API.Direction.OUTPUT)
    String name;
    @API(help="Column Type", direction=API.Direction.OUTPUT)
    String type;
    @API(help="Column Format (printf)", direction=API.Direction.OUTPUT)
    String format;
    @API(help="Column Description", direction=API.Direction.OUTPUT)
    String description;
  }

  @API(help="Table Name", direction=API.Direction.OUTPUT)
  public String name;

  @API(help="Column Specification", direction=API.Direction.OUTPUT)
  public ColumnSpecs[] columns;

  @API(help="Number of Rows", direction=API.Direction.OUTPUT)
  public int rowcount;

  @API(help="Table Data (col-major)", direction=API.Direction.OUTPUT)
  public String[][] data;

  /**
   * Fill a TwoDimTable Schema from a TwoDimTable
   * @param t TwoDimTable
   * @return TwoDimTableSchema
   */
  @Override public TwoDimTableV1 fillFromImpl(TwoDimTable t) {
    name = t.getTableHeader();
    final int cols = t.getColDim()+1;
    final int rows = t.getRowDim();
    rowcount = rows;
    columns = new ColumnSpecs[cols];
    columns[0] = new ColumnSpecs();
    columns[0].name = "";
    columns[0].type = "string"; //Ugly: Should be an Enum in TwoDimTable class
    columns[0].format = "%s";
    columns[0].description = null;
    for (int c=1; c<cols; ++c) {
      columns[c] = new ColumnSpecs();
      columns[c].name = t.getColHeaders()[c-1];
      columns[c].type = t.getColTypes()[c-1];
      columns[c].format = t.getColFormats()[c-1];
      columns[c].description = null; //TODO: Add description
    }
    data = new String[cols][rows];
    data[0] = new String[t.getRowDim()];
    for (int r=0; r<t.getRowDim(); ++r) {
      data[0][r] = t.getRowHeaders()[r];
    }
    for (int c=1; c<cols; ++c) {
      data[c] = new String[rows];
      for (int r=0; r<rows; ++r) {
        data[c][r] = t.get(r,c-1) != null ? t.get(r,c-1).toString() : null; // don't use String.format(): client is supposed to format
      }
    }
    return this;
  }

  /**
   * Fill a TwoDimTable from this Schema
   * @param impl
   * @return
   */
  public TwoDimTable fillImpl(TwoDimTable impl) {
    final int rows = data[0].length;
    assert(rows == rowcount);
    final int cols = data.length+1;
    String tableHeader = name;
    String[] rowHeaders = new String[rows];
    for (int r=0; r<rows; ++r) {
      rowHeaders[r] = data[0][r];
    }
    String[] colHeaders = new String[cols];
    colHeaders[0] = "";
    for (int c=1; c<cols; ++c) {
      colHeaders[c] = columns[c].name;
    }
    String[] colTypes = new String[cols];
    colTypes[0] = "";
    for (int c=1; c<cols; ++c) {
      colTypes[c] = columns[c].type;
    }
    String[] colFormats = new String[cols];
    colFormats[0] = "%s";
    for (int c=1; c<cols; ++c) {
      colFormats[c] = columns[c].format;
    }
    String[][] strCellValues = new String[rows][cols];
    double[][] dblCellValues = new double[rows][cols];
    for (int r=0; r<data[0].length; ++r) {
      for (int c=0; c<data.length; ++c) {
        try {
          if (columns[c].format == "string") {
            strCellValues[r][c] = data[c][r];
          }
          else if (columns[c].format == "double") {
            dblCellValues[r][c] = Double.parseDouble(data[c][r]);
          }
          else if (columns[c].format == "float") {
            dblCellValues[r][c] = Float.parseFloat(data[c][r]);
          }
          else if (columns[c].format == "integer") {
            dblCellValues[r][c] = Integer.parseInt(data[c][r]);
          }
          else if (columns[c].format == "long") {
            dblCellValues[r][c] = Long.parseLong(data[c][r]);
          }
          else throw H2O.unimpl();
        } catch (ClassCastException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return new TwoDimTable(tableHeader, rowHeaders, colHeaders, colTypes, colFormats, strCellValues, dblCellValues);
  }
}