package water.util;

import java.io.Serializable;
import water.util.Java7.Objects;

/**
 * Gathers stats for a byte array.
 * 
 * The array has to contain at least one NewLine character,
 * otherwise it's considered bad, bad.
 * 
 * Created by vpatryshev on 3/27/17.
 */
public class BytesStats implements Serializable {
  public final int numLines;
  public final int maxWidth;
  public final int numChars; // number non-newline bytes till the last NL

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BytesStats)) return false;
    BytesStats that = (BytesStats) o;
    return numLines == that.numLines &&
        maxWidth == that.maxWidth &&
        numChars == that.numChars;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(numLines, maxWidth, numChars);
  }

  public BytesStats(int numLines, int maxWidth, int numChars) {
    this.numLines = numLines;
    this.maxWidth = maxWidth;
    this.numChars = numChars;
  }
  
  public BytesStats(byte[] bytes, int offset) {
    int nl = 0;
    int mw = 0;
    int p = offset;
    int l = 0;
    for (int i = offset; i < bytes.length; i++) {
      if (bytes[i] == '\n') {
        l += i-p;
        mw = Math.max(mw, i - p);
        p = i+1;
        nl++;
      }
    }
    if (nl == 0) {
      mw = -1;
    }
    numLines = nl; // > 0
    maxWidth = mw; // >= 0
    numChars = l;
  }

  public BytesStats(byte[] bytes) {
    this(bytes, 0);
  }
  
  public int averageWidth() {
    // the logic is this: number of chars, minus number of NLs, averaging to middle.
    return numChars == 0 || numLines == 0 ? -1 : (numChars + numLines/2) / numLines;
  }

  @Override
  public String toString() {
    return "BytesStats{" +
        "numLines=" + numLines +
        ", maxWidth=" + maxWidth +
        ", numChars=" + numChars +
        '}';
  }
}
