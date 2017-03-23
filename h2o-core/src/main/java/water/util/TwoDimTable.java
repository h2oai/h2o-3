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
  private String     tableDescription;
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
   * @param tableDescription the table description
   * @param rowHeaders R-dim array for row headers
   * @param colHeaders  C-dim array for column headers
   * @param colTypes  C-dim array for column types
   * @param colFormats C-dim array with printf format strings for each column
   * @param colHeaderForRowHeaders column header for row headers
   */
  public TwoDimTable(String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
                     String[] colFormats, String colHeaderForRowHeaders) {
    if (tableHeader == null)
      tableHeader = "";
    if (tableDescription == null)
      tableDescription = "";
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
        if (!(colTypes[c].equals("double") || colTypes[c].equals("float") || colTypes[c].equals("int") ||
            colTypes[c].equals("long") || colTypes[c].equals("string")))
          throw new IllegalArgumentException("colTypes values must be one of \"double\", \"float\", \"int\", \"long\", or \"string\"");
      }
    }

    if (colFormats == null) {
      colFormats = new String[colDim];
      Arrays.fill(colFormats, "%s");
    }
    else if (colFormats.length != colDim)
      throw new IllegalArgumentException("colFormats must have the same length as colHeaders");

    this.tableHeader = tableHeader;
    this.tableDescription = tableDescription;
    this.rowHeaders = rowHeaders;
    this.colHeaders = colHeaders;
    this.colTypes = colTypes;
    this.colFormats = colFormats;
    this.cellValues = new IcedWrapper[rowDim][colDim];
  }

  /**
   * Constructor for TwoDimTable (R rows, C columns)
   * @param tableHeader the table header
   * @param tableDescription the table description
   * @param rowHeaders R-dim array for row headers
   * @param colHeaders  C-dim array for column headers
   * @param colTypes  C-dim array for column types
   * @param colFormats C-dim array with printf format strings for each column
   * @param colHeaderForRowHeaders column header for row headers
   * @param strCellValues String[R][C] array for string cell values, can be null (can provide String[R][], for example)
   * @param dblCellValues double[R][C] array for double cell values, can be empty (marked with emptyDouble - happens when initialized with double[R][])
   */
  public TwoDimTable(String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
                     String[] colFormats, String colHeaderForRowHeaders, String[][] strCellValues, double[][] dblCellValues) {
    this(tableHeader, tableDescription, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);

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
          for (int r = 0; r < rowDim; ++r)
            set(r, c, dblCellValues[r][c]);
          break;
        case "int":
        case "long":
          for (int r = 0; r < rowDim; ++r) {
            double val = dblCellValues[r][c];
            if (isEmpty(val))
              set(r, c, Double.NaN);
            else if ((long)val==val)
              set(r, c, (long)val);
            else
              set(r, c, val);
          }
          break;
        case "string":
          for (int r = 0; r < rowDim; ++r)
            set(r, c, strCellValues[r][c]);
          break;
        default:
          throw new IllegalArgumentException("Column type " + colTypes[c] + " is not supported.");
      }
    }
  }

  /**
   * Accessor for table cells
   * @param row a row index
   * @param col a column index
   * @return Object (either String or Double or Float or Integer or Long)
   */
  public Object get(final int row, final int col) { return cellValues[row][col] == null ? null : cellValues[row][col].get(); }

  public String getTableHeader() { return tableHeader; }

  public String getTableDescription() { return tableDescription; }

  public String[] getRowHeaders() { return rowHeaders; }

  public String[] getColHeaders() { return colHeaders; }

  public String getColHeaderForRowHeaders() { return colHeaderForRowHeaders; }

  public String[] getColTypes() { return colTypes; }

  public String[] getColFormats() { return colFormats; }

  public IcedWrapper[][] getCellValues() { return cellValues; }

  /**
   * Get row dimension
   * @return int
   */
  public int getRowDim() { return rowHeaders.length; }

  /**
   * Get col dimension
   * @return int
   */
  public int getColDim() { return colHeaders.length; }

  /**
   * Need to change table header when we are calling GLRM from PCA.
   *
   * @param newHeader: String containing new table header.
   */
  public void setTableHeader(String newHeader) {
    if (!StringUtils.isNullOrEmpty(newHeader)) {
      this.tableHeader = newHeader;
    }
  }

  /**
   * Setter for table cells
   * @param row a row index
   * @param col a column index
   * @param o Object value
   */
  public void set(final int row, final int col, final Object o) {
    if (o == null)
      cellValues[row][col] = new IcedWrapper(null);
    else if (o instanceof Double && Double.isNaN((double)o))
      cellValues[row][col] = new IcedWrapper(Double.NaN);
    else if (o instanceof int[])
      cellValues[row][col] = new IcedWrapper(Arrays.toString((int[])o));
    else if (o instanceof long[])
      cellValues[row][col] = new IcedWrapper(Arrays.toString((long[])o));
    else if (o instanceof float[])
      cellValues[row][col] = new IcedWrapper(Arrays.toString((float[])o));
    else if (o instanceof double[])
      cellValues[row][col] = new IcedWrapper(Arrays.toString((double[])o));
    else if (colTypes[col]=="string")
      cellValues[row][col] = new IcedWrapper(o.toString());
    else
      cellValues[row][col] = new IcedWrapper(o);
  }

  /**
   * Print table to String, using 2 spaces for padding between columns
   * @return String containing the ASCII version of the table
   */
  public String toString() {
    return toString(2, true);
  }
  /**
   * Print table to String, using user-given padding
   * @param pad number of spaces for padding between columns
   * @return String containing the ASCII version of the table
   */
  public String toString(final int pad) {
    return toString(pad, true);
  }

  private static int PRINTOUT_ROW_LIMIT = 20;
  private boolean skip(int row) {
    assert(PRINTOUT_ROW_LIMIT % 2 == 0);
    if (getRowDim() <= PRINTOUT_ROW_LIMIT) return false;
    if (row <= PRINTOUT_ROW_LIMIT/2) return false;
    if (row >= getRowDim()-PRINTOUT_ROW_LIMIT/2) return false;
    return true;
  }
  /**
   * Print table to String, using user-given padding
   * @param pad number of spaces for padding between columns
   * @param full whether to print the full table (otherwise top 5 and bottom 5 rows only)
   * @return String containing the ASCII version of the table
   */
  public String toString(final int pad, boolean full) {
    if (pad < 0)
      throw new IllegalArgumentException("pad must be a non-negative integer");

    final int rowDim = getRowDim();
    final int colDim = getColDim();

    final int actualRowDim = full ? rowDim : Math.min(PRINTOUT_ROW_LIMIT+1, rowDim);

    final String[][] cellStrings = new String[actualRowDim + 1][colDim + 1];
    for (String[] row: cellStrings)
      Arrays.fill(row, "");

    cellStrings[0][0] = colHeaderForRowHeaders != null ? colHeaderForRowHeaders : "";
    int row = 0;
    for (int r = 0; r < rowDim; ++r) {
      if (!full && skip(r)) continue;
      cellStrings[row+1][0] = rowHeaders[r];
      row++;
    }
    for (int c = 0; c < colDim; ++c)
      cellStrings[0][c+1] = colHeaders[c];

    for (int c = 0; c < colDim; ++c) {
      final String formatString = colFormats[c];
      row = 0;
      for (int r = 0; r < rowDim; ++r) {
        if (!full && skip(r)) continue;
        Object o = get(r,c);
        if ((o == null) || o instanceof Double && isEmpty((double)o)){
          cellStrings[row + 1][c + 1] = "";
          row++;
          continue;
        } else if (o instanceof Double && Double.isNaN((double)o)) {
          cellStrings[row + 1][c + 1] = "NaN";
          row++;
          continue;
        }
        try {
          if (o instanceof Double) cellStrings[row + 1][c + 1] = String.format(formatString, (Double) o);
          else if (o instanceof Float) cellStrings[row + 1][c + 1] = String.format(formatString, (Float) o);
          else if (o instanceof Integer) cellStrings[row + 1][c + 1] = String.format(formatString, (Integer) o);
          else if (o instanceof Long) cellStrings[row + 1][c + 1] = String.format(formatString, (Long) o);
          else if (o instanceof String) cellStrings[row + 1][c + 1] = (String)o;
          else cellStrings[row + 1][c + 1] = String.format(formatString, cellValues[r][c]);
        } catch(Throwable t) {
          cellStrings[row + 1][c + 1] = o.toString();
        }
        row++;
      }
    }

    final int[] colLen = new int[colDim + 1];
    for (int c = 0; c <= colDim; ++c) {
      for (int r = 0; r <= actualRowDim; ++r) {
        colLen[c] = Math.max(colLen[c], cellStrings[r][c].length());
      }
    }

    final StringBuilder sb = new StringBuilder();
    if (tableHeader.length() > 0) {
      sb.append(tableHeader);
    }
    if (tableDescription.length() > 0) {
      sb.append(" (").append(tableDescription).append(")");
    }
    sb.append(":\n");
    for (int r = 0; r <= actualRowDim; ++r) {
      int len = colLen[0];
      if (actualRowDim != rowDim && r - 1 == PRINTOUT_ROW_LIMIT/2) {
        assert(!full);
        sb.append("---");
      } else {
        if (len > 0)
          sb.append(String.format("%" + colLen[0] + "s", cellStrings[r][0]));
        for (int c = 1; c <= colDim; ++c) {
          len = colLen[c];
          if (len > 0)
            sb.append(String.format("%" + (len + pad) + "s", cellStrings[r][c].equals("null") ? "" : cellStrings[r][c]));
        }
      }
      sb.append("\n");
    }

   return sb.toString();
  }

}
