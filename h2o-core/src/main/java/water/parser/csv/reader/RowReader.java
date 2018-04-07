package water.parser.csv.reader;

import org.apache.commons.lang.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class RowReader {

  private static final char LF = '\n';
  private static final char CR = '\r';

  private static final int FIELD_MODE_RESET = 0;
  private static final int FIELD_MODE_QUOTED = 1;
  private static final int FIELD_MODE_NON_QUOTED = 2;
  private static final int FIELD_MODE_QUOTE_ON = 4;

  private final char fieldSeparator;
  private final char textDelimiter;
  private final byte[][] buf = new byte[2][];
  private final Line line = new Line(32);
  private final ReusableStringBuilder currentField = new ReusableStringBuilder();
  private long bufPos;
  private int prevChar = -1;
  private long bufferLength;
  private long copyStart;
  private boolean finished;
  private long lastLineStart = 0;

  public RowReader(final byte[] bytes, final char fieldSeparator, final char textDelimiter) {
    Objects.requireNonNull(fieldSeparator);
    Objects.requireNonNull(textDelimiter);

    this.fieldSeparator = fieldSeparator;
    this.textDelimiter = textDelimiter;
    this.buf[0] = bytes;
    this.buf[1] = new byte[]{};
    this.bufferLength = buf[0].length + buf[1].length;
  }


  public Line readLine() throws IOException {
    this.lastLineStart = bufPos;
    // get fields local for higher performance
    final Line localLine = line.reset();
    final ReusableStringBuilder localCurrentField = currentField;

    int copyLen = 0;
    int fieldMode = FIELD_MODE_RESET;
    int lines = 1;

    while (true) {
      if (bufferLength == bufPos) {
          finished = true;
          break;
      }

      final byte c = getCharAt(bufPos++);

      if ((fieldMode & FIELD_MODE_QUOTE_ON) != 0) {
        if (c == textDelimiter) {
          // End of quoted text
          fieldMode &= ~FIELD_MODE_QUOTE_ON;
          if (copyLen > 0) {
            localCurrentField.append(getRange(copyStart, copyLen));
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
            localCurrentField.append(getRange(copyStart, copyLen));
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
            localCurrentField.append(getRange(copyStart, copyLen));
          }
          localLine.addField(localCurrentField.toStringAndReset());
          prevChar = c;
          copyStart = bufPos;
          break;
        } else if (c == LF) {
          if (prevChar != CR) {
            if (copyLen > 0) {
              localCurrentField.append(getRange(copyStart, copyLen));
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

  public byte[] getRange(long start, long length) {
    if (length < buf[0].length) {
      return ArrayUtils.subarray(buf[0], (int) start, (int) (start + length));
    } else {
      byte[] mainArrayChunk = ArrayUtils.subarray(buf[0], (int) start, buf[0].length);
      byte[] overflowChunkArray = ArrayUtils.subarray(buf[1], 0, (int) (length - mainArrayChunk.length));

      return water.util.ArrayUtils.append(mainArrayChunk, overflowChunkArray);
    }


  }

  public byte getCharAt(long position) {
    if (position > buf[0].length - 1) {
      return buf[1][(int) position - (buf[0].length - 1)];
    } else {
      return buf[0][(int) position];
    }
  }

  public void revertLastLine() {
    this.bufPos = lastLineStart;
    this.prevChar = getCharAt(Math.max(bufPos - 1, 0));
  }

  public void appendBytes(byte[] bytes) {
    buf[1] = bytes;
    bufferLength = buf[0].length + buf[1].length;
  }


  public boolean isFinished() {
    return finished;
  }

  public static final class Line {

    @Override
    public String toString() {
      return "Line{" +
          "fields=" + Arrays.toString(fields) +
          '}';
    }

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
