package water.api.schemas3;

import water.AutoBuffer;
import water.H2O;
import water.Iced;
import water.IcedWrapper;
import water.api.API;
import water.util.TwoDimTable;

/**
 * Client-facing Schema of a TwoDimTable
 * Notes:
 * 1) We embed the rowHeaders into the table, extending it by 1 column
 * 2) We store all the data in column-major order
 * 3) We store all the data in String format
 *
 */
public class TwoDimTableV3 extends SchemaV3<TwoDimTable, TwoDimTableV3> {

  public static class ColumnSpecsBase extends SchemaV3<Iced, ColumnSpecsBase> {
    @API(help="Column Name", direction=API.Direction.OUTPUT)
    public String name; // allow reset of col header names so that I can undo the pythonize for GLM multinomial coeff names
    @API(help="Column Type", direction=API.Direction.OUTPUT)
    String type;
    @API(help="Column Format (printf)", direction=API.Direction.OUTPUT)
    String format;
    @API(help="Column Description", direction=API.Direction.OUTPUT)
    String description;
  }

  @API(help="Table Name", direction=API.Direction.OUTPUT)
  public String name;

  @API(help="Table Description", direction=API.Direction.OUTPUT)
  public String description;

  @API(help="Column Specification", direction=API.Direction.OUTPUT)
  public ColumnSpecsBase[] columns;

  @API(help="Number of Rows", direction=API.Direction.OUTPUT)
  public int rowcount;

  @API(help="Table Data (col-major)", direction=API.Direction.OUTPUT)
  public IcedWrapper[][] data;

  public TwoDimTableV3() {}
  public TwoDimTableV3(TwoDimTable impl) { super(impl); }

  /**
   * Fill a TwoDimTable Schema from a TwoDimTable
   * @param t TwoDimTable
   * @return TwoDimTableSchema
   */
  @Override
  public TwoDimTableV3 fillFromImpl(TwoDimTable t) {
    name = t.getTableHeader();
    description = t.getTableDescription();
    final int rows = t.getRowDim();
    rowcount = rows;
    boolean have_row_header_cols = t.getColHeaderForRowHeaders() != null;
    for (int r=0; r<rows; ++r) {
      if (!have_row_header_cols) break;
      have_row_header_cols &= t.getRowHeaders()[r] != null;
    }
    if (have_row_header_cols) {
      final int cols = t.getColDim()+1;
      columns = new ColumnSpecsBase[cols];
      columns[0] = new ColumnSpecsBase();
      columns[0].name = pythonify(t.getColHeaderForRowHeaders());
      columns[0].type = "string";
      columns[0].format = "%s";
      columns[0].description = t.getColHeaderForRowHeaders();
      for (int c = 1; c < cols; ++c) {
        columns[c] = new ColumnSpecsBase();
        columns[c].name = pythonify(t.getColHeaders()[c - 1]);
        columns[c].type = t.getColTypes()[c - 1];
        columns[c].format = t.getColFormats()[c - 1];
        columns[c].description = t.getColHeaders()[c - 1];
      }
      data = new IcedWrapper[cols][rows];
      data[0] = new IcedWrapper[t.getRowDim()];
      for (int r = 0; r < t.getRowDim(); ++r) {
        data[0][r] = new IcedWrapper(t.getRowHeaders()[r]);
      }
      IcedWrapper[][] cellValues = t.getCellValues();
      for (int c = 1; c < cols; ++c) {
        data[c] = new IcedWrapper[rows];
        for (int r = 0; r < rows; ++r) {
          data[c][r] = cellValues[r][c - 1];
        }
      }
    } else {
      final int cols = t.getColDim();
      columns = new ColumnSpecsBase[cols];
      for (int c = 0; c < cols; ++c) {
        columns[c] = new ColumnSpecsBase();
        columns[c].name = pythonify(t.getColHeaders()[c]);
        columns[c].type = t.getColTypes()[c];
        columns[c].format = t.getColFormats()[c];
        columns[c].description = t.getColHeaders()[c];
      }
      data = new IcedWrapper[cols][rows];
      IcedWrapper[][] cellValues = t.getCellValues();
      for (int c = 0; c < cols; ++c) {
        data[c] = new IcedWrapper[rows];
        for (int r = 0; r < rows; ++r) {
          data[c][r] = cellValues[r][c];
        }
      }
    }
    return this;
  }

  /**
   * Turn a description such as "Avg. Training MSE" into a JSON-usable field name "avg_training_mse"
   * @param n
   * @return
   */
  private String pythonify(String n) {
    if (n == null || name.toLowerCase().contains("confusion")) return n;
    StringBuilder sb = new StringBuilder();
    String [] modified = n.split("[\\s_]+");
    for (int i=0; i<modified.length; ++i) {
      if (i!=0) sb.append("_");
      String s = modified[i];
//      if (!s.matches("^[A-Z]{2,3}$")) {
        sb.append(s.toLowerCase()); //everything goes lowercase
//      } else {
//        sb.append(s);
//      }
    }
    String newString = sb.toString().replaceAll("[^\\w]", "");
//    if (!newString.equals(name)) {
//      Log.warn("Turning column description into field name: " + name + " --> " + newString);
//    }
    return newString;
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
    String tableDescription = description;
    String colHeaderForRowHeaders = columns[0].name;
    String[] rowHeaders = new String[rows];
    for (int r=0; r<rows; ++r) {
      rowHeaders[r] = (String)data[0][r].get();
    }
    String[] colHeaders = new String[cols];
    colHeaders[0] = "";
    for (int c=1; c<cols; ++c) {
      colHeaders[c] = columns[c].description;
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
          if (columns[c].format.equals("string")) {  // switch(String) is not java1.6 compliant!
            strCellValues[r][c] = (String)data[c][r].get();
          }
          else if (columns[c].format.equals("double")) {
            dblCellValues[r][c] = (Double)data[c][r].get();
          }
          else if (columns[c].format.equals("float")) {
            dblCellValues[r][c] = (Float)data[c][r].get();
          }
          else if (columns[c].format.equals("int")) {
            dblCellValues[r][c] = (Integer)data[c][r].get();
          }
          else if (columns[c].format.equals("long")) {
            dblCellValues[r][c] = (Long)data[c][r].get();
          }
          else throw H2O.fail();
        } catch (ClassCastException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return new TwoDimTable(tableHeader, tableDescription, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders, strCellValues, dblCellValues);
  }

  public final AutoBuffer writeJSON_impl(AutoBuffer ab) {
    ab.putJSONStr("name",name);
    ab.put1(',');
    ab.putJSONStr("description",description);
    ab.put1(',');
    ab.putJSONStr("columns").put1(':');
    ab.put1('[');
    if( columns!=null ) {
      for (int i = 0; i < columns.length; ++i) {
        columns[i].writeJSON(ab);
        if (i < columns.length - 1) ab.put1(',');
      }
    }
    ab.put1(']');
    ab.put1(',');
    ab.putJSON4("rowcount", rowcount);
    ab.put1(',');
    ab.putJSONStr("data").put1(':');
    ab.put1('[');
    if( data!=null ) {
      for (int i = 0; i < data.length; ++i) {
        ab.put1('[');
        for (int j = 0; j < data[i].length; ++j) {
          if (data[i][j] == null || data[i][j].get() == null) {
            ab.putJNULL();
          } else {
            data[i][j].writeUnwrappedJSON(ab);
          }
          if (j < data[i].length - 1) ab.put1(',');
        }
        ab.put1(']');
        if (i < data.length - 1) ab.put1(',');
      }
    }
      ab.put1(']');
    return ab;
  }
}
