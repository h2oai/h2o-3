package water.hadoop;

import water.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Simple class to help serialize messages from the Mapper to the Driver.
 */
class AbstractMessage {
  public static final char TYPE_UNKNOWN = 0;
  public static final char TYPE_EOF_NO_MESSAGE = 1;

  // Readers
  // -------
  protected int readBytes(Socket s, byte[] b) throws Exception {
    int bytesRead = 0;
    int bytesExpected = b.length;
    InputStream is = s.getInputStream();
    while (bytesRead < bytesExpected) {
      int n = is.read(b, bytesRead, bytesExpected - bytesRead);
      if (n < 0) {
        return n;
      }
      bytesRead += n;
    }

    return bytesRead;
  }

  protected char readType(Socket s) throws Exception {
    byte b[] = new byte[1];
    int n = readBytes(s, b);
    if (n < 0) {
      return TYPE_EOF_NO_MESSAGE;
    }
    // System.out.println("readType: " + b[0]);
    return (char) b[0];
  }

  protected int readInt(Socket s) throws Exception {
    byte b[] = new byte[4];
    int n = readBytes(s, b);
    if (n < 0) {
      throw new IOException("AbstractMessage: readBytes failed");
    }

    int i =
            (
                    ((((int) b[0]) << (8*0)) & 0x000000ff) |
                    ((((int) b[1]) << (8*1)) & 0x0000ff00) |
                    ((((int) b[2]) << (8*2)) & 0x00ff0000) |
                    ((((int) b[3]) << (8*3)) & 0xff000000)
            );
    // System.out.println("readInt: " + i);
    // System.out.println("readInt: " + b[0] + " " + b[1] + " " + b[2] + " " + b[3]);

    return i;
  }

  protected String readString(Socket s) throws Exception {
    int length = readInt(s);

    byte b[] = new byte[length];
    int n = readBytes(s, b);
    if (n < 0) {
      throw new IOException("AbstractMessage: readBytes failed");
    }

    String str = new String(b, "UTF-8");
    // System.out.println("readString: " + str);
    return str;
  }

  // Writers
  // -------
  protected void writeBytes(Socket s, byte[] b) throws Exception {
    OutputStream os = s.getOutputStream();
    os.write(b);
  }

  protected void writeType(Socket s, int type) throws Exception {
    byte b[] = new byte[1];
    b[0] = (byte)(char)type;
    writeBytes(s, b);
  }

  protected void writeInt(Socket s, int i) throws Exception {
    byte b[] = new byte[4];
    b[0] = (byte)((i >> (8*0)) & 0xff);
    b[1] = (byte)((i >> (8*1)) & 0xff);
    b[2] = (byte)((i >> (8*2)) & 0xff);
    b[3] = (byte)((i >> (8*3)) & 0xff);
    // System.out.println("writeInt: " + b[0] + " " + b[1] + " " + b[2] + " " + b[3]);
    writeBytes(s, b);
  }

  protected void writeString(Socket s, String str) throws Exception {
    byte b[] = StringUtils.bytesOf(str);
    writeInt(s, b.length);
    writeBytes(s, b);
  }
}
