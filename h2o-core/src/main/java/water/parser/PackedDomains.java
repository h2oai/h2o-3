package water.parser;

import water.util.StringUtils;

import java.io.*;
import java.util.Arrays;

import static water.util.ArrayUtils.*;
/**
 * Recreated "packed domains" functionality, with tests
 * 
 * Created by vpatryshev on 4/12/17.
 */
public class PackedDomains {
  public static int sizeOf(byte[] domain) {
    return encodeAsInt(domain, 0);
  }
  
  public static String[] unpackToStrings(byte[] domain) {
    int n = sizeOf(domain);
    String[] out = new String[n];
    int p = 4;
    for (int i = 0; i < n; i++) {
      int p0 = p;
      while (domain[p] != 0 && p++ < domain.length);
      out[i] = StringUtils.toString(domain, p0, p - p0);
      p++;
    }
    return out;
  }


  /** this one is for testing */
  static byte[] pack(String... source) {
    BufferedString[] bss = new BufferedString[source.length];
    for (int i = 0; i < source.length; i++) {
      bss[i] = new BufferedString(source[i]);
    }
    return pack(bss);
  }

  private static final byte[] ZEROES = new byte[]{0,0,0,0};
  
  public static byte[] pack(BufferedString[] source) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      out.write(ZEROES);

      for (BufferedString bs : source) {
        out.write(bs.getBuffer(), bs.getOffset(), bs.length());
        out.write(ZEROES, 0, 1);
      }
    } catch (IOException ignore) {}
    byte[] bytes = out.toByteArray();
    decodeAsInt(source.length, bytes, 0);
    return bytes;
  }
  
  private static void dump(InputStream from, OutputStream to) {
    try {
      int b;
      while ((b = from.read()) >= 0) to.write(b);
    } catch (IOException ignore) {}
  }
  
  private static void dumpWord(InputStream from, OutputStream to) {
    try {
      int b;
      while ((b = from.read()) > 0) to.write(b);
      to.write(0);
    } catch (IOException ignore) {}
  }
  
  public static byte[] merge(byte[] as, byte[] bs) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    InputStream ia = new ByteArrayInputStream(as, 4, as.length);
    InputStream ib = new ByteArrayInputStream(bs, 4, bs.length);
    
    try {
      out.write(ZEROES);
      ia.mark(0);
      ib.mark(0);
      while (ia.available() > 0 || ib.available() > 0) {
        if (ia.available() == 0) dump(ib, out);
        else
        if (ib.available() == 0) dump(ia, out);
        else {
          int a = ia.read();
          int b = ib.read();
          if (a == b) {
            out.write(a);
            if (a == 0) {
              ia.mark(0); ib.mark(0);
            }
          }
          else if (a < b) {
            out.write(a); 
            if (a != 0) dumpWord(ia, out);
            ia.mark(0);
            ib.reset();
          } else {
            out.write(b); 
            if (b != 0) dumpWord(ib, out);
            ib.mark(0);
            ia.reset();
          }
        }
      }
    } catch (IOException ignore) {}
    byte[] bytes = out.toByteArray();
    int n = 0;
    for (byte b : bytes) if (b == 0) n++;
    decodeAsInt(n - 4, bytes, 0);
    return bytes;
  }
}
