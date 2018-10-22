package hex.genmodel.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtils {

  public static void copyStream(InputStream source, OutputStream target) throws IOException {
    byte[] buffer = new byte[8 * 1024];
    while (true) {
      int len = source.read(buffer);
      if (len == -1)
        break;
      target.write(buffer, 0, len);
    }
  }

}
