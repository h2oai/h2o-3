package water.util;

import com.google.common.base.Function;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import java.io.*;

class SampleCode implements Function<Integer, String> {
  public String apply(Integer x) {
    return "X=" + x;
  }
}

@SuppressWarnings("unchecked")
public class ClosureTest {

  public static byte[] readHex(String s) {

    String hexes = "0123456789abcdef";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    for (char c : s.toCharArray()) {
      int i = hexes.indexOf(Character.toLowerCase(c));
      if (i >= 0) baos.write(i);
    }
    byte[] nibbles = baos.toByteArray();
    byte[] out = new byte[nibbles.length/2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte)((nibbles[i*2]<<4) | nibbles[i*2+1]);
    }
    return out;
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSerialization() throws Exception {
    final MoveableCode moveableCode = new MoveableCode(SampleCode.class);
    byte[] bytes = moveableCode.code_for_testing();
    String s = moveableCode.dumpCode();
    assertArrayEquals(bytes, readHex(s));
  }

  @Test
  public void testDeserialization() throws Exception {
    byte[] bytes = readHex(sampleCode);
    Class c = new MoveableCode("water.util.SampleClosure", bytes).loadClass();
    assertTrue(Function.class.isAssignableFrom(c));
  }

  @Ignore("vlad: will work on taking care of inner classes")
  @Test
  public void testDeserializationOfRealLifeFSample() throws Exception {
    byte[] bytes = readHex(rlSampleCode);
    Class c = new MoveableCode("water.fvec.UdfVecTest.$1", bytes).loadClass();
    assertTrue(Function.class.isAssignableFrom(c));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeserializationAndEval() throws Exception {
    byte[] bytes = new MoveableCode(SampleCode.class).code_for_testing();

    Function<Integer, String> f = new MoveableCode<Function<Integer, String>>(SampleCode.class.getCanonicalName(), bytes).instantiate();
    assertEquals("X=42", f.apply(42));
    assertEquals("X=12345", f.apply(12345));
  }

  @Test
  public void testClosure() throws Exception {
    Function<Integer, String> f = new SampleCode();

    Closure<Integer, String> c = new Closure<>(f);

    assertEquals("X=42", c.apply(42));
    assertEquals("X=12345", c.apply(12345));

  }

  String sampleCode = 
                  "ca fe ba be 00 00 00 33 00 2d 0a 00 0b 00 1d 07 \n" +
                  "00 1e 0a 00 02 00 1d 08 00 1f 0a 00 02 00 20 0a \n" +
                  "00 02 00 21 0a 00 02 00 22 07 00 23 0a 00 0a 00 \n" +
                  "24 07 00 25 07 00 26 07 00 27 01 00 06 3c 69 6e \n" +
                  "69 74 3e 01 00 03 28 29 56 01 00 04 43 6f 64 65 \n" +
                  "01 00 0f 4c 69 6e 65 4e 75 6d 62 65 72 54 61 62 \n" +
                  "6c 65 01 00 12 4c 6f 63 61 6c 56 61 72 69 61 62 \n" +
                  "6c 65 54 61 62 6c 65 01 00 04 74 68 69 73 01 00 \n" +
                  "1a 4c 77 61 74 65 72 2f 75 74 69 6c 2f 53 61 6d \n" +
                  "70 6c 65 43 6c 6f 73 75 72 65 3b 01 00 05 61 70 \n" +
                  "70 6c 79 01 00 27 28 4c 6a 61 76 61 2f 6c 61 6e \n" +
                  "67 2f 49 6e 74 65 67 65 72 3b 29 4c 6a 61 76 61 \n" +
                  "2f 6c 61 6e 67 2f 53 74 72 69 6e 67 3b 01 00 01 \n" +
                  "78 01 00 13 4c 6a 61 76 61 2f 6c 61 6e 67 2f 49 \n" +
                  "6e 74 65 67 65 72 3b 01 00 26 28 4c 6a 61 76 61 \n" +
                  "2f 6c 61 6e 67 2f 4f 62 6a 65 63 74 3b 29 4c 6a \n" +
                  "61 76 61 2f 6c 61 6e 67 2f 4f 62 6a 65 63 74 3b \n" +
                  "01 00 09 53 69 67 6e 61 74 75 72 65 01 00 5a 4c \n" +
                  "6a 61 76 61 2f 6c 61 6e 67 2f 4f 62 6a 65 63 74 \n" +
                  "3b 4c 63 6f 6d 2f 67 6f 6f 67 6c 65 2f 63 6f 6d \n" +
                  "6d 6f 6e 2f 62 61 73 65 2f 46 75 6e 63 74 69 6f \n" +
                  "6e 3c 4c 6a 61 76 61 2f 6c 61 6e 67 2f 49 6e 74 \n" +
                  "65 67 65 72 3b 4c 6a 61 76 61 2f 6c 61 6e 67 2f \n" +
                  "53 74 72 69 6e 67 3b 3e 3b 01 00 0a 53 6f 75 72 \n" +
                  "63 65 46 69 6c 65 01 00 1b 43 6c 61 73 73 53 65 \n" +
                  "72 69 61 6c 69 7a 61 74 69 6f 6e 54 65 73 74 2e \n" +
                  "6a 61 76 61 0c 00 0d 00 0e 01 00 17 6a 61 76 61 \n" +
                  "2f 6c 61 6e 67 2f 53 74 72 69 6e 67 42 75 69 6c \n" +
                  "64 65 72 01 00 02 58 3d 0c 00 28 00 29 0c 00 28 \n" +
                  "00 2a 0c 00 2b 00 2c 01 00 11 6a 61 76 61 2f 6c \n" +
                  "61 6e 67 2f 49 6e 74 65 67 65 72 0c 00 14 00 15 \n" +
                  "01 00 18 77 61 74 65 72 2f 75 74 69 6c 2f 53 61 \n" +
                  "6d 70 6c 65 43 6c 6f 73 75 72 65 01 00 10 6a 61 \n" +
                  "76 61 2f 6c 61 6e 67 2f 4f 62 6a 65 63 74 01 00 \n" +
                  "1f 63 6f 6d 2f 67 6f 6f 67 6c 65 2f 63 6f 6d 6d \n" +
                  "6f 6e 2f 62 61 73 65 2f 46 75 6e 63 74 69 6f 6e \n" +
                  "01 00 06 61 70 70 65 6e 64 01 00 2d 28 4c 6a 61 \n" +
                  "76 61 2f 6c 61 6e 67 2f 53 74 72 69 6e 67 3b 29 \n" +
                  "4c 6a 61 76 61 2f 6c 61 6e 67 2f 53 74 72 69 6e \n" +
                  "67 42 75 69 6c 64 65 72 3b 01 00 2d 28 4c 6a 61 \n" +
                  "76 61 2f 6c 61 6e 67 2f 4f 62 6a 65 63 74 3b 29 \n" +
                  "4c 6a 61 76 61 2f 6c 61 6e 67 2f 53 74 72 69 6e \n" +
                  "67 42 75 69 6c 64 65 72 3b 01 00 08 74 6f 53 74 \n" +
                  "72 69 6e 67 01 00 14 28 29 4c 6a 61 76 61 2f 6c \n" +
                  "61 6e 67 2f 53 74 72 69 6e 67 3b 00 20 00 0a 00 \n" +
                  "0b 00 01 00 0c 00 00 00 03 00 00 00 0d 00 0e 00 \n" +
                  "01 00 0f 00 00 00 2f 00 01 00 01 00 00 00 05 2a \n" +
                  "b7 00 01 b1 00 00 00 02 00 10 00 00 00 06 00 01 \n" +
                  "00 00 00 11 00 11 00 00 00 0c 00 01 00 00 00 05 \n" +
                  "00 12 00 13 00 00 00 01 00 14 00 15 00 01 00 0f \n" +
                  "00 00 00 48 00 02 00 02 00 00 00 14 bb 00 02 59 \n" +
                  "b7 00 03 12 04 b6 00 05 2b b6 00 06 b6 00 07 b0 \n" +
                  "00 00 00 02 00 10 00 00 00 06 00 01 00 00 00 13 \n" +
                  "00 11 00 00 00 16 00 02 00 00 00 14 00 12 00 13 \n" +
                  "00 00 00 00 00 14 00 16 00 17 00 01 10 41 00 14 \n" +
                  "00 18 00 01 00 0f 00 00 00 33 00 02 00 02 00 00 \n" +
                  "00 09 2a 2b c0 00 08 b6 00 09 b0 00 00 00 02 00 \n" +
                  "10 00 00 00 06 00 01 00 00 00 11 00 11 00 00 00 \n" +
                  "0c 00 01 00 00 00 09 00 12 00 13 00 00 00 02 00 \n" +
                  "19 00 00 00 02 00 1a 00 1b 00 00 00 02 00 1c";

  String rlSampleCode =
          "ca fe ba be 00 00 00 33 00 3a 09 00 0a 00 23 0a \n" +
          "00 0b 00 24 0a 00 08 00 25 06 3f 1a 36 e2 eb 1c \n" +
          "43 2d 0a 00 26 00 27 0a 00 28 00 29 07 00 2a 0a \n" +
          "00 0a 00 2b 07 00 2c 07 00 2d 07 00 2e 01 00 06 \n" +
          "74 68 69 73 24 30 01 00 17 4c 77 61 74 65 72 2f \n" +
          "66 76 65 63 2f 55 64 66 56 65 63 54 65 73 74 3b \n" +
          "01 00 06 3c 69 6e 69 74 3e 01 00 1a 28 4c 77 61 \n" +
          "74 65 72 2f 66 76 65 63 2f 55 64 66 56 65 63 54 \n" +
          "65 73 74 3b 29 56 01 00 04 43 6f 64 65 01 00 0f \n" +
          "4c 69 6e 65 4e 75 6d 62 65 72 54 61 62 6c 65 01 \n" +
          "00 12 4c 6f 63 61 6c 56 61 72 69 61 62 6c 65 54 \n" +
          "61 62 6c 65 01 00 04 74 68 69 73 01 00 0c 49 6e \n" +
          "6e 65 72 43 6c 61 73 73 65 73 01 00 19 4c 77 61 \n" +
          "74 65 72 2f 66 76 65 63 2f 55 64 66 56 65 63 54 \n" +
          "65 73 74 24 31 3b 01 00 05 61 70 70 6c 79 01 00 \n" +
          "24 28 4c 6a 61 76 61 2f 6c 61 6e 67 2f 4c 6f 6e \n" +
          "67 3b 29 4c 6a 61 76 61 2f 6c 61 6e 67 2f 44 6f \n" +
          "75 62 6c 65 3b 01 00 01 78 01 00 10 4c 6a 61 76 \n" +
          "61 2f 6c 61 6e 67 2f 4c 6f 6e 67 3b 01 00 26 28 \n" +
          "4c 6a 61 76 61 2f 6c 61 6e 67 2f 4f 62 6a 65 63 \n" +
          "74 3b 29 4c 6a 61 76 61 2f 6c 61 6e 67 2f 4f 62 \n" +
          "6a 65 63 74 3b 01 00 09 53 69 67 6e 61 74 75 72 \n" +
          "65 01 00 57 4c 6a 61 76 61 2f 6c 61 6e 67 2f 4f \n" +
          "62 6a 65 63 74 3b 4c 63 6f 6d 2f 67 6f 6f 67 6c \n" +
          "65 2f 63 6f 6d 6d 6f 6e 2f 62 61 73 65 2f 46 75 \n" +
          "6e 63 74 69 6f 6e 3c 4c 6a 61 76 61 2f 6c 61 6e \n" +
          "67 2f 4c 6f 6e 67 3b 4c 6a 61 76 61 2f 6c 61 6e \n" +
          "67 2f 44 6f 75 62 6c 65 3b 3e 3b 01 00 0a 53 6f \n" +
          "75 72 63 65 46 69 6c 65 01 00 0f 55 64 66 56 65 \n" +
          "63 54 65 73 74 2e 6a 61 76 61 01 00 0f 45 6e 63 \n" +
          "6c 6f 73 69 6e 67 4d 65 74 68 6f 64 07 00 2f 0c \n" +
          "00 30 00 31 0c 00 0d 00 0e 0c 00 0f 00 31 0c 00 \n" +
          "32 00 33 07 00 34 0c 00 35 00 36 07 00 37 0c 00 \n" +
          "38 00 39 01 00 0e 6a 61 76 61 2f 6c 61 6e 67 2f \n" +
          "4c 6f 6e 67 0c 00 17 00 18 01 00 17 77 61 74 65 \n" +
          "72 2f 66 76 65 63 2f 55 64 66 56 65 63 54 65 73 \n" +
          "74 24 31 01 00 10 6a 61 76 61 2f 6c 61 6e 67 2f \n" +
          "4f 62 6a 65 63 74 01 00 1f 63 6f 6d 2f 67 6f 6f \n" +
          "67 6c 65 2f 63 6f 6d 6d 6f 6e 2f 62 61 73 65 2f \n" +
          "46 75 6e 63 74 69 6f 6e 01 00 15 77 61 74 65 72 \n" +
          "2f 66 76 65 63 2f 55 64 66 56 65 63 54 65 73 74 \n" +
          "01 00 10 74 65 73 74 53 69 6e 65 46 75 6e 63 74 \n" +
          "69 6f 6e 01 00 03 28 29 56 01 00 09 6c 6f 6e 67 \n" +
          "56 61 6c 75 65 01 00 03 28 29 4a 01 00 0e 6a 61 \n" +
          "76 61 2f 6c 61 6e 67 2f 4d 61 74 68 01 00 03 73 \n" +
          "69 6e 01 00 04 28 44 29 44 01 00 10 6a 61 76 61 \n" +
          "2f 6c 61 6e 67 2f 44 6f 75 62 6c 65 01 00 07 76 \n" +
          "61 6c 75 65 4f 66 01 00 15 28 44 29 4c 6a 61 76 \n" +
          "61 2f 6c 61 6e 67 2f 44 6f 75 62 6c 65 3b 00 20 \n" +
          "00 0a 00 0b 00 01 00 0c 00 01 10 10 00 0d 00 0e \n" +
          "00 00 00 03 00 00 00 0f 00 10 00 01 00 11 00 00 \n" +
          "00 3e 00 02 00 02 00 00 00 0a 2a 2b b5 00 01 2a \n" +
          "b7 00 02 b1 00 00 00 02 00 12 00 00 00 06 00 01 \n" +
          "00 00 00 13 00 13 00 00 00 16 00 02 00 00 00 0a \n" +
          "00 14 00 16 00 00 00 00 00 0a 00 0d 00 0e 00 01 \n" +
          "00 01 00 17 00 18 00 01 00 11 00 00 00 44 00 04 \n" +
          "00 02 00 00 00 10 2b b6 00 03 8a 14 00 04 6b b8 \n" +
          "00 06 b8 00 07 b0 00 00 00 02 00 12 00 00 00 06 \n" +
          "00 01 00 00 00 15 00 13 00 00 00 16 00 02 00 00 \n" +
          "00 10 00 14 00 16 00 00 00 00 00 10 00 19 00 1a \n" +
          "00 01 10 41 00 17 00 1b 00 01 00 11 00 00 00 33 \n" +
          "00 02 00 02 00 00 00 09 2a 2b c0 00 08 b6 00 09 \n" +
          "b0 00 00 00 02 00 12 00 00 00 06 00 01 00 00 00 \n" +
          "13 00 13 00 00 00 0c 00 01 00 00 00 09 00 14 00 \n" +
          "16 00 00 00 04 00 1c 00 00 00 02 00 1d 00 1e 00 \n" +
          "00 00 02 00 1f 00 20 00 00 00 04 00 21 00 22 00 \n" +
          "15 00 00 00 0a 00 01 00 0a 00 00 00 00 00 00 ";
}
