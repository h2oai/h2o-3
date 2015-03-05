package water.util;

import water.AutoBuffer;
import water.Iced;
import water.IcedWrapper;

import java.util.Arrays;

/**
 * Serializable 2D Table containing Strings or doubles
 * Table can be named
 * Columns and Rows can be named
 * Fields can be empty
 */
public class TwoDimTable extends Iced {
  private String     tableHeader;
  private String[]   rowHeaders;
  private String[]   colHeaders;
  private String[]   colTypes;
  private String[]   colFormats;
  private IcedWrapper[][] cellValues;
  private String     colHeaderForRowHeaders;

  //public static final double emptyDouble = Double.longBitsToDouble(0x7ff8000000000100L); //also a NaN, but not Double.NaN (0x7ff8000000000000)
  public static final double emptyDouble = Double.MIN_VALUE*2; //Some unlikely value

  /**
   * Check whether a double value is considered an "empty field".
   * @param d a double value
   * @return true iff d represents an empty field
   */
  public static boolean isEmpty(final double d) {
    return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(emptyDouble);
  }

  /**
   * Constructor for TwoDimTable (R rows, C columns)
   * @param tableHeader the table header
   * @param rowHeaders R-dim array for row headers
   * @param colHeaders  C-dim array for column headers
   * @param colTypes  C-dim array for column types
   * @param colFormats C-dim array with printf format strings for each column
   * @param colHeaderForRowHeaders column header for row headers
   */
  public TwoDimTable(String tableHeader, String[] rowHeaders, String[] colHeaders, String[] colTypes,
                     String[] colFormats, String colHeaderForRowHeaders) {
    if (tableHeader == null)
      tableHeader = "";
    this.colHeaderForRowHeaders = colHeaderForRowHeaders;

    if (rowHeaders == null)
      throw new IllegalArgumentException("rowHeaders is null");
    else {
      for (int r = 0; r < rowHeaders.length; ++r)
        if (rowHeaders[r] == null)
          rowHeaders[r] = "";
    }

    if (colHeaders == null)
      throw new IllegalArgumentException("colHeaders is null");
    else {
      for (int c = 0; c < colHeaders.length; ++c)
        if (colHeaders[c] == null)
          colHeaders[c] = "";
    }

    final int rowDim = rowHeaders.length;
    final int colDim = colHeaders.length;

    if (colTypes == null) {
      colTypes = new String[colDim];
      Arrays.fill(colTypes, "string");
    }
    else if (colTypes.length != colDim)
      throw new IllegalArgumentException("colTypes must have the same length as colHeaders");
    else {
      for (int c = 0; c < colDim; ++c) {
        colTypes[c] = colTypes[c].toLowerCase();
        if (!(colTypes[c].equals("double") || colTypes[c].equals("float") || colTypes[c].equals("integer") ||
            colTypes[c].equals("long") || colTypes[c].equals("string")))
          throw new IllegalArgumentException("colTypes values must be one of \"double\", \"float\", \"integer\", \"long\", or \"string\"");
      }
    }

    if (colFormats == null) {
      colFormats = new String[colDim];
      Arrays.fill(colFormats, "%s");
    }
    else if (colFormats.length != colDim)
      throw new IllegalArgumentException("colFormats must have the same length as colHeaders");

    this.tableHeader = tableHeader;
    this.rowHeaders = rowHeaders;
    this.colHeaders = colHeaders;
    this.colTypes = colTypes;
    this.colFormats = colFormats;
    this.cellValues = new IcedWrapper[rowDim][colDim];
  }

  /**
   * Constructor for TwoDimTable (R rows, C columns)
   * @param tableHeader the table header
   * @param rowHeaders R-dim array for row headers
   * @param colHeaders  C-dim array for column headers
   * @param colTypes  C-dim array for column types
   * @param colFormats C-dim array with printf format strings for each column
   * @param colHeaderForRowHeaders column header for row headers
   * @param strCellValues String[R][C] array for string cell values, can be null (can provide String[R][], for example)
   * @param dblCellValues double[R][C] array for double cell values, can be empty (marked with emptyDouble - happens when initialized with double[R][])
   */
  public TwoDimTable(String tableHeader, String[] rowHeaders, String[] colHeaders, String[] colTypes,
                     String[] colFormats, String colHeaderForRowHeaders, String[][] strCellValues, double[][] dblCellValues) {
    this(tableHeader, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);

    assert (isEmpty(emptyDouble));
    assert (!Arrays.equals(new AutoBuffer().put8d(emptyDouble).buf(), new AutoBuffer().put8d(Double.NaN).buf()));

    final int rowDim = rowHeaders.length;
    final int colDim = colHeaders.length;

    for (int c = 0; c < colDim; ++c) {
      if (colTypes[c].equalsIgnoreCase("string")) {
        for (String[] vec : strCellValues) {
          if (vec == null)
            throw new IllegalArgumentException("Null string in strCellValues");
          if (vec.length != colDim)
            throw new IllegalArgumentException("Each row in strCellValues must have the same length as colHeaders");
        }
        break;
      }
    }
    for (int c = 0; c < colDim; ++c) {
      if (!colTypes[c].equalsIgnoreCase("string")) {
        for (double[] vec : dblCellValues) {
          if (vec.length != colDim)
            throw new IllegalArgumentException("Each row in dblCellValues must have the same length as colHeaders");
        }
        break;
      }
    }

    for (int r = 0; r < rowDim; ++r) {
      for (int c = 0; c < colDim; ++c) {
        if (strCellValues[r] != null && strCellValues[r][c] != null &&
            dblCellValues[r] != null && !isEmpty(dblCellValues[r][c]))
          throw new IllegalArgumentException("Cannot provide both a String and a Double at row " + r + " and column " + c + ".");
      }
    }

    for (int c = 0; c < colDim; ++c) {
      switch (colTypes[c]) {
        case "double":
        case "float":
        case "integer":
        case "long":
          for (int r = 0; r < rowDim; ++r)
            set(r, c, dblCellValues[r][c]);
          break;
        default:
          for (int r = 0; r < rowDim; ++r)
            set(r, c, strCellValues[r][c]);
      }
    }
  }

  /**
   * Accessor for table cells
   * @param row a row index
   * @param col a column index
   * @return Object (either String or Double or Float or Integer or Long)
   */
  public Object get(final int row, final int col) {
    return cellValues[row][col] == null ? null : cellValues[row][col].get();
  }

  public String getTableHeader() {
    return tableHeader;
  }

  public String[] getRowHeaders() {
    return rowHeaders;
  }

  public String[] getColHeaders() { return colHeaders; }

  public String getColHeaderForRowHeaders() { return colHeaderForRowHeaders; }

  public String[] getColTypes() {
    return colTypes;
  }

  public String[] getColFormats() {
    return colFormats;
  }

  public IcedWrapper[][] getCellValues() {
    return cellValues;
  }

  /**
   * Get row dimension
   * @return int
   */
  public int getRowDim() {
    return rowHeaders.length;
  }

  /**
   * Get row dimension
   * @return int
   */
  public int getColDim() {
    return colHeaders.length;
  }

  /**
   * Setter for table cells
   * @param row a row index
   * @param col a column index
   * @param o Object value
   */
  public void set(final int row, final int col, final Object o) {
    if (o == null) cellValues[row][col] = null;

    if (colTypes[col]=="double")
      cellValues[row][col] = new IcedWrapper(new Double(o.toString()));
    else if (colTypes[col]=="float")
      cellValues[row][col] = new IcedWrapper(new Float(o.toString()));
    else if (colTypes[col]=="integer")
      cellValues[row][col] = new IcedWrapper(new Integer(o.toString()));
    else if (colTypes[col]=="long")
      cellValues[row][col] = new IcedWrapper(new Long(o.toString()));
    else
      cellValues[row][col] = new IcedWrapper(o);
  }

  /**
   * Print table to String, using 2 spaces for padding between columns
   * @return String containing the ASCII version of the table
   */
  public String toString() {
    return toString(2);
  }

  /**
   * Print table to String, using user-given padding
   * @param pad number of spaces for padding between columns
   * @return String containing the ASCII version of the table
   */
  public String toString(final int pad) {
    if (pad < 0)
      throw new IllegalArgumentException("pad must be a non-negative integer");

    final int rowDim = getRowDim();
    final int colDim = getColDim();

    final String[][] cellStrings = new String[rowDim + 1][colDim + 1];
    for (String[] row: cellStrings)
      Arrays.fill(row, "");

    cellStrings[0][0] = colHeaderForRowHeaders != null ? colHeaderForRowHeaders : "";
    for (int r = 0; r < rowDim; ++r)
      cellStrings[r+1][0] = rowHeaders[r];
    for (int c = 0; c < colDim; ++c)
      cellStrings[0][c+1] = colHeaders[c];

    for (int c = 0; c < colDim; ++c) {
      final String formatString = colFormats[c];
      switch (colTypes[c]) {
        case "double":
          for (int r = 0; r < rowDim; ++r) {
            cellStrings[r + 1][c + 1] = cellValues[r][c] == null || cellValues[r][c].get() == null ? "" : String.format(formatString, (Double)cellValues[r][c].get());
          }
          break;
        case "float":
          for (int r = 0; r < rowDim; ++r) {
            cellStrings[r + 1][c + 1] = cellValues[r][c] == null || cellValues[r][c].get() == null ? "" : String.format(formatString, (Float)cellValues[r][c].get());
          }
          break;
        case "integer":
          for (int r = 0; r < rowDim; ++r) {
            cellStrings[r + 1][c + 1] = cellValues[r][c] == null || cellValues[r][c].get() == null ? "" : String.format(formatString, (Integer)cellValues[r][c].get());
          }
          break;
        case "long":
          for (int r = 0; r < rowDim; ++r) {
            cellStrings[r + 1][c + 1] = cellValues[r][c] == null || cellValues[r][c].get() == null ? "" : String.format(formatString, (Long)cellValues[r][c].get());
          }
          break;
        default:
          for (int r = 0; r < rowDim; ++r)
            if (cellValues[r][c] != null && cellValues[r][c].get() != null)
              cellStrings[r+1][c+1] = String.format(formatString, cellValues[r][c]);
          break;
      }
    }

    final int[] colLen = new int[colDim + 1];
    for (int c = 0; c <= colDim; ++c)
      for (int r = 0; r <= rowDim; ++r)
        colLen[c] = Math.max(colLen[c], cellStrings[r][c].length());

    final StringBuilder sb = new StringBuilder();
    if (tableHeader.length() > 0) {
      sb.append(tableHeader);
      sb.append(":\n");
    }
    for (int r = 0; r <= rowDim; ++r) {
      int len = colLen[0];
      if (len > 0)
        sb.append(String.format("%" + colLen[0] + "s", cellStrings[r][0]));
      for (int c = 1; c <= colDim; ++c) {
        len = colLen[c];
        if (len > 0)
          sb.append(String.format("%" + (len + pad) + "s", cellStrings[r][c].equals("null") ? "" : cellStrings[r][c]));
      }
      sb.append("\n");
    }

   return sb.toString();
  }

}
