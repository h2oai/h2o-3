package water.util;

/**
 * Small helper class for creating Markdown in a StringBuffer.
 */
public class MarkdownBuilder {
  private StringBuffer sb;

  public MarkdownBuilder() {
    sb = new StringBuffer();
  }

  public StringBuffer append(StringBuffer s) {
    sb.append(s);
    return sb;
  }

  public StringBuffer append(String s) {
    sb.append(s);
    return sb;
  }

  public StringBuffer paragraph(String paragraph) {
    sb.append(paragraph).append("\n\n");
    return sb;
  }

  public StringBuffer hline() {
    sb.append("---\n");
    return sb;
  }

  private StringBuffer append(String separator, boolean addNewline, String... strings) {
    int i = 0;
    for (String string : strings) {
      if (i++ > 0) sb.append(separator);
      sb.append(string);
    }
    if (addNewline)
      sb.append("\n");
    return sb;
  }

  public StringBuffer comment(String... comment) {
    sb.append("[//]: # (");
    this.append(" ", false, comment);
    sb.append(")\n");
    return sb;
  }

  public StringBuffer heading1(String... heading) {
    sb.append("# ");
    this.append(" ", true, heading);
    return sb;
  }

  public StringBuffer heading2(String... heading) {
    sb.append("## ");
    this.append(" ", true, heading);
    return sb;
  }

  public StringBuffer tableRow(String... cols) {
    this.append(" | ", true, cols);
    return sb;
  }

  public StringBuffer tableHeader(String... cols) {
    tableRow(cols);
    int i = 0;
    for (String col : cols) {
      if (i++ > 0) sb.append(" | ");
      sb.append("---");
    }
    sb.append("\n");
    return sb;
  }

  public StringBuffer stringBuffer() {
    return sb;
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
