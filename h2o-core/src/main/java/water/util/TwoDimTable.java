package water.util;

import water.Iced;

public class TwoDimTable extends Iced {
  String description;
  String[] colNames;
  String[] colFormatStrings; //optional
  String[] rowHeaders;
  String[][] strings;
  Double[][] doubles;

  public TwoDimTable(String description, String[] colNames, String[] colFormatStrings, String[] rowHeaders, String[][] strings, Double[][] doubles) {
    if (description == null) throw new IllegalArgumentException("description is missing.");
    if (colNames == null) throw new IllegalArgumentException("colNames are missing.");
    if (colFormatStrings == null) throw new IllegalArgumentException("colFormatStrings are missing.");
    if (rowHeaders == null) throw new IllegalArgumentException("rowHeaders are missing.");
    if (strings == null) throw new IllegalArgumentException("string values are missing.");
    if (strings.length != rowHeaders.length)
      throw new IllegalArgumentException("string values must have the same length as rowHeaders: " + rowHeaders.length);
    if (doubles == null) throw new IllegalArgumentException("double values are missing.");
    if (doubles.length != rowHeaders.length)
      throw new IllegalArgumentException("double values must have the same length as rowHeaders: " + rowHeaders.length);

    for (String[] v : strings) {
      if (v != null)
      if (v.length != colNames.length)
        throw new IllegalArgumentException("Each entry in string values must have the same length as colNames: " + colNames.length);
    }
    for (Double[] v : doubles) {
      if (v != null)
      if (v.length != colNames.length)
        throw new IllegalArgumentException("Each entry in string values must have the same length as colNames: " + colNames.length);
    }

    if (colFormatStrings != null && colFormatStrings.length != colNames.length)
      throw new IllegalArgumentException("colFormatStrings must have the same length as colNames: " + colNames.length);
    this.description = description;
    this.colNames = colNames;
    this.colFormatStrings = colFormatStrings;
    this.rowHeaders = rowHeaders;
    this.strings = strings;
    this.doubles = doubles;
    checkConsistency();
  }

  void checkConsistency() {
    for (int r=0; r<rowHeaders.length; ++r) {
      for (int c=0; c<colNames.length; ++c) {
        if (this.strings[r] != null && this.strings[r][c] != null && this.doubles[r] != null && this.doubles[r][c] != null)
          throw new IllegalArgumentException("Cannot provide both a String and a Double at row idx " + r + " and column idx " + c + ".");
      }
    }
  }

  public Object get(int row, int col) {
    return strings[row][col] != null ? strings[row][col]
            : doubles[row][col] != null ? doubles[row][col] : null;
  }

  public void set(int row, int col, String s) {
    strings[row][col] = s;
    checkConsistency();
  }

  public void set(int row, int col, Double d) {
    doubles[row][col] = d;
    checkConsistency();
  }

  public String toString() {
    int pad = 2;

    StringBuilder sb = new StringBuilder();

    // First pass to figure out cell sizes
    int maxRowNameLen = rowHeaders[0].length();
    for (int r=1; r<rowHeaders.length; ++r) {
      maxRowNameLen = Math.max(maxRowNameLen, rowHeaders[r].length());
    }
    maxRowNameLen += pad;
    int[] colLen = new int[colNames.length];
    for (int c=0; c<colNames.length; ++c) {
      colLen[c] = colNames[c].length();
      for (int r=0; r<rowHeaders.length; ++r) {
        if (strings[r] != null && strings[r][c] != null)
          colLen[c] = Math.max(colLen[c], String.format(colFormatStrings[c], strings[r][c]).length());
        else if (doubles[r] != null && doubles[r][c] != null)
          colLen[c] = Math.max(colLen[c], String.format(colFormatStrings[c], doubles[r][c]).length());
      }
      colLen[c] += pad;
    }

    // Print column header
    sb.append(description).append(":\n");
    for (int i=0; i<maxRowNameLen; ++i) sb.append(" ");
    for (int c=0; c<colNames.length; ++c)
      sb.append(String.format("%" + colLen[c] + "s", colNames[c]));
    sb.append("\n");

    // Print table entries row by row
    for (int r=0; r<rowHeaders.length; ++r) {
      sb.append(String.format("%" + maxRowNameLen + "s", rowHeaders[r]));
      for (int c=0; c<colNames.length; ++c) {
        if (strings[r] != null && strings[r][c] != null)
          sb.append(String.format("%" + colLen[c] + "s", String.format(colFormatStrings[c], strings[r][c])));
        else if (doubles[r] != null && doubles[r][c] != null)
          sb.append(String.format("%" + colLen[c] + "s", String.format(colFormatStrings[c], doubles[r][c])));
        else
          sb.append(String.format("%" + colLen[c] + "s", "")); //empty field
      }
      sb.append("\n");
    }
    return sb.toString();
  }


}