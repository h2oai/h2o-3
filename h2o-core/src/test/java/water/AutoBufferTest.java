package water;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.*;

public class AutoBufferTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testOutputStreamBigDataBigChunks() {
    // don't run if the JVM doesn't have enough memory
    Assume.assumeTrue("testOutputStreamBigDataBigChunks: JVM has enough memory (~4GB)",
            Runtime.getRuntime().maxMemory() > 4e9);
    final int dataSize = (Integer.MAX_VALUE - 8) /*=max array size*/ - 5 /*=array header size*/;
    byte[] data = new byte[dataSize - 1];
    AutoBuffer ab = new AutoBuffer(NullOutputStream.NULL_OUTPUT_STREAM, false);
    // make sure the buffer can take the full array
    ab.putA1(data);
    // now try to stream 1TB of data through the buffer
    for (int i = 0; i < 512; i++) {
      if (i % 10 == 0)
        System.out.println(i);
      ab.putA1(data);
    }
    ab.close();
  }

  @Test
  public void testOutputStreamBigDataSmallChunks() {
    final int dataSize = 100 * 1024;
    byte[] data = new byte[dataSize - 1];
    AutoBuffer ab = new AutoBuffer(NullOutputStream.NULL_OUTPUT_STREAM, false);
    // make sure the buffer can take full array
    ab.putA1(data);
    // try to stream 1TB of data made of small chunks through AutoBuffer
    for (int i = 0; i < 1e12 / dataSize; i++)
      ab.putA1(data);
    ab.close();
  }

  @Test
  public void testOutputStreamSmallData() {
    final int dataSize = 100 * 1024;
    byte[] data = new byte[dataSize];
    AutoBuffer ab = new AutoBuffer(NullOutputStream.NULL_OUTPUT_STREAM, false);
    // stream bite-size data to AutoBuffer
    for (int i = 0; i < Integer.MAX_VALUE / dataSize; i++)
      ab.putA1(data);
    ab.close();
  }

  static class XYZZY implements Serializable {
    int i = 1;
    String s = "hi";
  }

  @Test
  public void testNameOfClass() throws Exception {
    
    byte[] bytes = AutoBuffer.javaSerializeWritePojo(new XYZZY());
    assertEquals("water.AutoBufferTest$XYZZY", AutoBuffer.nameOfClass(bytes));
    bytes[7] = 127;
    assertEquals("water.AutoBufferTest$XYZZY\ufffd\27\142\51\140\ufffd\ufffd\174\2\0\2I\0\1", AutoBuffer.nameOfClass(bytes));
    bytes[7] = 1;
    assertEquals("wat", AutoBuffer.nameOfClass(bytes));
    bytes[7] = -10;
    assertEquals("wat", AutoBuffer.nameOfClass(bytes));
    assertEquals("(null)", AutoBuffer.nameOfClass(null));
    assertEquals("(no name)", AutoBuffer.nameOfClass(new byte[]{0,0,0,0,0}));
  }

}