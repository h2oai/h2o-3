package hex.genmodel.attributes.parameters;

import java.io.Serializable;
import java.util.Objects;

public class ColumnSpecifier implements Serializable {

  private final String columnName;
  private final String[] is_member_of_frames;

  public ColumnSpecifier(String columnName, String[] is_member_of_frames) {
    Objects.requireNonNull(columnName);
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
