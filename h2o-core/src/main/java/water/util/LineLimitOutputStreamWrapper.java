package water.util;

import java.io.IOException;
import java.io.OutputStream;

/** Simple {@code OutputStream} wrapper limiting
 * number of rows outputed into a given stream.
 *
 * It delegates all calls to underlying output stream,
 * but counts number of lines passed trough.
 *
 * Note: new line is detected only based on '\n' character!
 */
public class LineLimitOutputStreamWrapper extends OutputStream {

  /** Output stream to delegate writes */
  private final OutputStream os;

  /** Number of lines to output. */
  private final int lineLimit;

  /** Number of lines in output */
  private int linesCnt = 0;

  public LineLimitOutputStreamWrapper(OutputStream os, int lineLimit) {
    this.os = os;
    this.lineLimit = lineLimit;
  }

  @Override
  public void write(int b) throws IOException {
    if (linesCnt < lineLimit) {
      os.write(b);
      if (b == '\n') linesCnt++;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (linesCnt < lineLimit) {
      for (int i = 0; i < len; i++) {
        if (b[off + i] == '\n') linesCnt++;
        if (linesCnt == lineLimit) {
          len = off + i;
          break;
        }
      }
      os.write(b, off, len);
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    os.write(b);
  }

  @Override
  public void flush() throws IOException {
    os.flush();
  }

  @Override
  public void close() throws IOException {
    os.close();
  }
}
