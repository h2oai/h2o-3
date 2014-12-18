package water.util;

public class TwoDimTable {
  String description;
  String[] colNames;
  String[] colFormatStrings; //optional
  String[] rowNames;
  Object[][] values;

  /**
   * Full two-dim table constructor
   * @param colNames
   * @param colFormatStrings
   * @param rowNames
   * @param values
   */
  public TwoDimTable(String description, String[] colNames, String[] colFormatStrings, String[] rowNames, Object[][] values) {
    this.description = description;
    this.colNames = colNames;
    this.colFormatStrings = colFormatStrings;
    this.rowNames = rowNames;
    this.values = values;
  }

  /**
   * Construct empty two-dim table from row/column names
   * @param colNames
   * @param rowNames
   */
  public TwoDimTable(String description, String[] colNames, String[] colFormatStrings, String[] rowNames) {
    this.description = description;
    this.colNames = colNames;
    this.colFormatStrings = colFormatStrings;
    this.rowNames = rowNames;
    values = new Object[colNames.length][];
    for (int i=0; i<values.length; ++i) {
      values[i] = new Object[rowNames.length];
    }
  }

  public String toString() {
    int pad = 2;

    StringBuilder sb = new StringBuilder();

    // First pass to figure out cell sizes
    int maxRowNameLen = rowNames[0].length();
    for (int r=1; r<rowNames.length; ++r) {
      maxRowNameLen = Math.max(maxRowNameLen, rowNames[r].length());
    }
    maxRowNameLen += pad;
    int[] colLen = new int[colNames.length];
    for (int c=0; c<colNames.length; ++c) {
      colLen[c] = colNames[c].length();
      for (int r=0; r<rowNames.length; ++r) {
        colLen[c] = Math.max(colLen[c], String.format(colFormatStrings[c], values[r][c]).length());
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
    for (int r=0; r<rowNames.length; ++r) {
      sb.append(String.format("%" + maxRowNameLen + "s", rowNames[r]));
      for (int c=0; c<colNames.length; ++c)
        sb.append(String.format("%" + colLen[c] + "s", String.format(colFormatStrings[c], values[r][c])));
      sb.append("\n");
    }
    return sb.toString();
  }


}