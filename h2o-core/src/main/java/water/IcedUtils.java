package water;

import water.util.FileUtils;
import water.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Utility class to support Iced objects.
 */
public class IcedUtils {

  /** Deep-copy clone given iced object. */
  static public <T extends Iced> T deepCopy(T iced) {
    if (iced == null) return null;
    AutoBuffer ab = new AutoBuffer();
    iced.write(ab);
    ab.flipForReading();
    // Create a new instance
    return (T) TypeMap.newInstance(iced.frozenType()).read(ab);
  }

  public static byte[] compress(byte[] data) {
    ByteArrayOutputStream outputStream = null;
    try {
      int len = data.length;
      Deflater deflater;
      if (len<1000000)
//        deflater = new Deflater(Deflater.NO_COMPRESSION);
//      else if (len<10000)
        deflater = new Deflater(Deflater.BEST_SPEED);
      else
        deflater = new Deflater(Deflater.BEST_COMPRESSION);

      deflater.setInput(data);
      outputStream = new ByteArrayOutputStream(data.length);
      deflater.finish();
      byte[] buffer = new byte[1024];
      while (!deflater.finished()) {
        int count = deflater.deflate(buffer); // returns the generated code... index
        outputStream.write(buffer, 0, count);
      }
    } catch (Throwable t) {
      throw new RuntimeException("Compression failed.", t);
    } finally {
      FileUtils.close(outputStream);
    }
    byte[] output = outputStream.toByteArray();
    totalOrig += data.length;
    totalCompressed += output.length;
    counter++;
    if (counter%10 == 0)
      Log.info("Compression ratio: " + ((double)totalOrig / (double)totalCompressed));
    return output;
  }
  static long counter;
  static long totalOrig;
  static long totalCompressed;

  public static byte[] decompress(byte[] data) {
    ByteArrayOutputStream outputStream = null;
    try {
      Inflater inflater = new Inflater();
      inflater.setInput(data);
      outputStream = new ByteArrayOutputStream(data.length);
      byte[] buffer = new byte[1024];
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        outputStream.write(buffer, 0, count);
      }
      return outputStream.toByteArray();
    } catch(Throwable t) {
      throw new RuntimeException("Decompression failed.", t);
    } finally {
      FileUtils.close(outputStream);
    }
  }
}
