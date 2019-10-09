package hex.genmodel.attributes.parameters;

import java.io.Serializable;

public class ColumnSpecifier implements Serializable {

  private final String columnName;
  private final String[] is_member_of_frames;

  public ColumnSpecifier(String columnName, String[] is_member_of_frames) {
    this.columnName = columnName;
    this.is_member_of_frames = is_member_of_frames;
  }

  public String getColumnName() {
    return columnName;
  }

  public String[] getIsMemberOfFrames() {
    return is_member_of_frames;
  }
}
