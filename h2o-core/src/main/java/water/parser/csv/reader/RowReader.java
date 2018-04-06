package water.parser.csv.reader;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

public final class RowReader implements Closeable {

  private static final char LF = '\n';
  private static final char CR = '\r';
  private static final int BUFFER_SIZE = 8192;

  private static final int FIELD_MODE_RESET = 0;
  private static final int FIELD_MODE_QUOTED = 1;
  private static final int FIELD_MODE_NON_QUOTED = 2;
  private static final int FIELD_MODE_QUOTE_ON = 4;

  private final Reader reader;
  private final char fieldSeparator;
  private final char textDelimiter;
  private final char[] buf = new char[BUFFER_SIZE];
  private final Line line = new Line(32);
  private final ReusableStringBuilder currentField = new ReusableStringBuilder(512);
  private int bufPos;
  private int bufLen;
  private int prevChar = -1;
  private int copyStart;
  private boolean finished;
  private int lastLineStart = 0;

  public RowReader(final Reader reader, final char fieldSeparator, final char textDelimiter) {
    this.reader = reader;
    this.fieldSeparator = fieldSeparator;
    this.textDelimiter = textDelimiter;
  }

  /*
   * ugly, performance optimized code begins
   */
  public Line readLine() throws IOException {
    this.lastLineStart = bufPos;
    // get fields local for higher performance
    final Line localLine = line.reset();
    final ReusableStringBuilder localCurrentField = currentField;

    int copyLen = 0;
    int fieldMode = FIELD_MODE_RESET;
    int lines = 1;

    while (true) {
      if (bufLen == bufPos) {
        // end of buffer

        if (copyLen > 0) {
          localCurrentField.append(buf, copyStart, copyLen);
        }
        bufLen = reader.read(buf, 0, buf.length);

        if (bufLen < 0) {
          // end of data
          finished = true;

          if (prevChar == fieldSeparator || localCurrentField.hasContent()) {
            localLine.addField(localCurrentField.toStringAndReset());
          }

          break;
        }

        copyStart = bufPos = copyLen = 0;
      }

      final char c = buf[bufPos++];

      if ((fieldMode & FIELD_MODE_QUOTE_ON) != 0) {
        if (c == textDelimiter) {
          // End of quoted text
          fieldMode &= ~FIELD_MODE_QUOTE_ON;
          if (copyLen > 0) {
            localCurrentField.append(buf, copyStart, copyLen);
            copyLen = 0;
          }
          copyStart = bufPos;
        } else {
          if (c == CR || c == LF && prevChar != CR) {
            lines++;
          }
          copyLen++;
        }
      } else {
        if (c == fieldSeparator) {
          if (copyLen > 0) {
            localCurrentField.append(buf, copyStart, copyLen);
            copyLen = 0;
          }
          localLine.addField(localCurrentField.toStringAndReset());
          copyStart = bufPos;
          fieldMode = FIELD_MODE_RESET;
        } else if (c == textDelimiter && (fieldMode & FIELD_MODE_NON_QUOTED) == 0) {
          // Quoted text starts
          fieldMode = FIELD_MODE_QUOTED | FIELD_MODE_QUOTE_ON;

          if (prevChar == textDelimiter) {
            // escaped quote
            copyLen++;
          } else {
            copyStart = bufPos;
          }
        } else if (c == CR) {
          if (copyLen > 0) {
            localCurrentField.append(buf, copyStart, copyLen);
          }
          localLine.addField(localCurrentField.toStringAndReset());
          prevChar = c;
          copyStart = bufPos;
          break;
        } else if (c == LF) {
          if (prevChar != CR) {
            if (copyLen > 0) {
              localCurrentField.append(buf, copyStart, copyLen);
            }
            localLine.addField(localCurrentField.toStringAndReset());
            prevChar = c;
            copyStart = bufPos;
            break;
          }
          copyStart = bufPos;
        } else {
          copyLen++;
          if (fieldMode == FIELD_MODE_RESET) {
            fieldMode = FIELD_MODE_NON_QUOTED;
          }
        }
      }

      prevChar = c;
    }

    localLine.setLines(lines);
    return localLine;
  }

  public void revertLastLine() {
    this.bufPos = lastLineStart;
    this.prevChar = buf[Math.max(bufPos - 1, 0)];
  }

  public void appendBytes(byte[] bytes) {
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  public boolean isFinished() {
    return finished;
  }

  public static final class Line {

    private String[] fields;
    private int linePos;
    private int lines;

    Line(final int initialCapacity) {
      fields = new String[initialCapacity];
    }

    Line reset() {
      linePos = 0;
      lines = 1;
      return this;
    }

    void addField(final String field) {
      if (linePos == fields.length) {
        fields = Arrays.copyOf(fields, fields.length * 2);
      }
      fields[linePos++] = field;
    }

    public String[] getFields() {
      return Arrays.copyOf(fields, linePos);
    }

    public int getFieldCount() {
      return linePos;
    }

    public int getLines() {
      return lines;
    }

    void setLines(final int lines) {
      this.lines = lines;
    }
  }

}
