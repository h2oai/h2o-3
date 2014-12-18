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
    if (description == null) throw new IllegalArgumentException("description is missing.");
    if (colNames == null) throw new IllegalArgumentException("colNames are missing.");
    if (colFormatStrings == null) throw new IllegalArgumentException("colFormatStrings are missing.");
    if (rowNames == null) throw new IllegalArgumentException("rowNames are missing.");
    if (values == null) throw new IllegalArgumentException("values are missing.");
    if (values.length != rowNames.length)
      throw new IllegalArgumentException("values must have the same length as rowNames: " + rowNames.length);
    for (Object[] v : values)
      if (v.length != colNames.length)
        throw new IllegalArgumentException("Each entry in values must have the same length as colNames: " + colNames.length);
    if (colFormatStrings != null && colFormatStrings.length != colNames.length)
      throw new IllegalArgumentException("colFormatStrings must have the same length as colNames: " + colNames.length);
    this.description = description;
    this.colNames = colNames;
    this.colFormatStrings = colFormatStrings;
    this.rowNames = rowNames;
    this.values = values;
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