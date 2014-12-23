package water.util;

import water.Iced;

import java.util.Arrays;

/**
 * Serializable 2D Table containing Strings or doubles
 * Table can be named
 * Columns and Rows can be named
 * Fields can be empty
 */
public class TwoDimTable extends Iced {
  String tableHeader;
  String[] colHeaders;
  String[] colFormatStrings; //optional
  String[] rowHeaders;
  String[][] strCellValues;
  double[][] dblCellValues;

  public static final double emptyDouble = Double.longBitsToDouble(0x7ff8000000000100L); //also a NaN, but not Double.NaN

  /**
   * Check whether a double value is considered an "empty field".
   * @param d
   * @return true iff d represents an empty field
   */
  public static boolean isEmpty(double d) { return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(emptyDouble); }

  /**
   * Constructor for TwoDimTable (C columns, R rows)
   * @param tableHeader the table header
   * @param colHeaders  C-dim array for column headers
   * @param colFormatStrings C-dim array with printf format strings for each column
   * @param rowHeaders R-dim array for row headers
   * @param strCellValues String[R][C] array for string cell values, can be null (can provide String[R][], for example)
   * @param dblCellValues double[R][C] array for double cell values, can be empty (marked with emptyDouble - happens when initialized with double[R][])
   */
  public TwoDimTable(String tableHeader, String[] colHeaders, String[] colFormatStrings, String[] rowHeaders,
                     String[][] strCellValues, double[][] dblCellValues) {
    assert(Double.isNaN(emptyDouble));
    assert(isEmpty(emptyDouble));
    assert(!isEmpty(Double.NaN));

    if (tableHeader == null) throw new IllegalArgumentException("tableHeader is missing.");
    if (colHeaders == null) throw new IllegalArgumentException("colNames are missing.");
    if (colFormatStrings == null) {
      colFormatStrings = new String[colHeaders.length];
      Arrays.fill(colFormatStrings, "%s");
    }
    if (rowHeaders == null) throw new IllegalArgumentException("rowHeaders are missing.");
    if (strCellValues == null) throw new IllegalArgumentException("string values are missing.");
    if (strCellValues.length != rowHeaders.length)
      throw new IllegalArgumentException("string values must have the same length as rowHeaders: " + rowHeaders.length);
    if (dblCellValues == null) throw new IllegalArgumentException("double values are missing.");
    if (dblCellValues.length != rowHeaders.length)
      throw new IllegalArgumentException("double values must have the same length as rowHeaders: " + rowHeaders.length);

    for (String[] v : strCellValues) {
      if (v != null)
      if (v.length != colHeaders.length)
        throw new IllegalArgumentException("Each entry in string values must have the same length as colNames: " + colHeaders.length);
    }
    for (double[] v : dblCellValues) {
      if (v != null)
      if (v.length != colHeaders.length)
        throw new IllegalArgumentException("Each entry in string values must have the same length as colNames: " + colHeaders.length);
    }

    if (colFormatStrings.length != colHeaders.length)
      throw new IllegalArgumentException("colFormatStrings must have the same length as colNames: " + colHeaders.length);
    this.tableHeader = tableHeader;
    this.colHeaders = colHeaders;
    this.colFormatStrings = colFormatStrings;
    this.rowHeaders = rowHeaders;
    this.strCellValues = strCellValues;
    this.dblCellValues = dblCellValues;
    checkConsistency();
  }

  /**
   * Internal helper
   */
  private void checkConsistency() {
    for (int r=0; r<rowHeaders.length; ++r) {
      if (dblCellValues[r]==null) {
        dblCellValues[r] = new double[colHeaders.length];
        Arrays.fill(dblCellValues[r], emptyDouble);
      }
      if (strCellValues[r]==null) {
        strCellValues[r] = new String[colHeaders.length];
      }
      for (int c=0; c< colHeaders.length; ++c) {
        if (strCellValues[r] != null && strCellValues[r][c] != null && dblCellValues[r] != null && !isEmpty(dblCellValues[r][c]))
          throw new IllegalArgumentException("Cannot provide both a String and a Double at row idx " + r + " and column idx " + c + ".");
      }
    }
  }

  /**
   * Accessor for table cells
   * @param row
   * @param col
   * @return Object (either String or Double)
   */
  public Object get(int row, int col) {
    return strCellValues[row][col] != null ? strCellValues[row][col]
            : !isEmpty(dblCellValues[row][col]) ? dblCellValues[row][col] : null;
  }

  /**
   * Setter for table cells
   * @param row
   * @param col
   * @param s String value
   */
  public void set(int row, int col, String s) {
    strCellValues[row][col] = s;
    checkConsistency();
  }

  /**
   * Setter for table cells
   * @param row
   * @param col
   * @param d double value
   */
  public void set(int row, int col, double d) {
    dblCellValues[row][col] = d;
    checkConsistency();
  }

  /**
   * Setter for table cells
   * @param row
   * @param col
   * @param f float value
   */
  public void set(int row, int col, float f) {
    dblCellValues[row][col] = (double)f;
    checkConsistency();
  }

  /**
   * Setter for table cells
   * @param row
   * @param col
   * @param i integer value
   */
  public void set(int row, int col, int i) {
    dblCellValues[row][col] = (double)i;
    checkConsistency();
  }

  /**
   * Setter for table cells
   * @param row
   * @param col
   * @param l long value
   */
  public void set(int row, int col, long l) {
    if (l != (double)l) throw new IllegalArgumentException("Can't fit long value of " + l + " into double without loss.");
    dblCellValues[row][col] = (double)l;
    checkConsistency();
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
  public String toString(int pad) {

    StringBuilder sb = new StringBuilder();

    // First pass to figure out cell sizes
    int maxRowNameLen = rowHeaders[0] == null ? 0 : rowHeaders[0].length();
    for (int r=1; r<rowHeaders.length; ++r) {
      if (rowHeaders[r] != null) maxRowNameLen = Math.max(maxRowNameLen, rowHeaders[r].length());
    }
    if (maxRowNameLen != 0) maxRowNameLen += pad;
    int[] colLen = new int[colHeaders.length];
    for (int c=0; c< colHeaders.length; ++c) {
      colLen[c] = colHeaders[c].length();
      for (int r=0; r<rowHeaders.length; ++r) {
        String format = colFormatStrings[c];
        String x = "";          // Empty field
        if (strCellValues[r] != null && strCellValues[r][c] != null) {
          x = strCellValues[r][c];
        } else if (dblCellValues[r] != null && !isEmpty(dblCellValues[r][c])) {
          x = format.equals("%d") ? String.format(format, (int)dblCellValues[r][c]) : String.format(format, dblCellValues[r][c]);
          format = "%" + colLen[c] + "s";
        }
        colLen[c] = Math.max(colLen[c], String.format(format, x).length());
      }
      colLen[c] += pad;
    }

    // Print column header
    sb.append(tableHeader).append(":\n");
    for (int i=0; i<maxRowNameLen; ++i) sb.append(" ");
    for (int c=0; c< colHeaders.length; ++c)
      sb.append(String.format("%" + colLen[c] + "s", colHeaders[c]));
    sb.append("\n");

    // Print table entries row by row
    for (int r=0; r<rowHeaders.length; ++r) {
      if (rowHeaders[r] != null) sb.append(String.format("%" + maxRowNameLen + "s", rowHeaders[r]));
      for (int c=0; c< colHeaders.length; ++c) {
        final String format = colFormatStrings[c];
        String x = "";          // Empty field
        if (strCellValues[r] != null && strCellValues[r][c] != null) {
          x = String.format(format, strCellValues[r][c]);
        } else if (dblCellValues[r] != null && !isEmpty(dblCellValues[r][c])) {
          x = format.equals("%d") ? String.format(format, (int) dblCellValues[r][c]) : String.format(format, dblCellValues[r][c]);
        }
        sb.append(String.format("%" + colLen[c] + "s", x));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
