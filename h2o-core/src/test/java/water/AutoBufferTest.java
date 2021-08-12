package water;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AutoBufferTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testPutA8ArrayWithJustZeros() {
    long[] arr = new long[AutoBuffer.BBP_SML._size * 2];
    AutoBuffer ab = new AutoBuffer(H2O.SELF, (byte) 1);
    ab.putA8(arr);
    assertTrue(ab._bb.hasArray());
    assertFalse(ab._bb.isDirect());
    assertEquals(ab._bb.array().length, 16);

  }

  @Test
  public void testPutA8SmallBufferAfterTrimmed() {
    long[] arr = new long[AutoBuffer.BBP_SML._size * 2];
    arr[1000] = 42;
    AutoBuffer ab = new AutoBuffer(H2O.SELF, (byte) 1);
    ab.putA8(arr);
    assertTrue(ab._bb.hasArray());
    assertFalse(ab._bb.isDirect());
    assertEquals(ab._bb.array().length, 16);
  }

  @Test
  public void testPutA8BigBufferAfterTrimmed() {
    long[] arr = new long[AutoBuffer.BBP_SML._size * 2];
    Arrays.fill(arr, Long.MAX_VALUE);
    AutoBuffer ab = new AutoBuffer(H2O.SELF, (byte) 1);
    ab.putA8(arr);
    assertFalse(ab._bb.hasArray());
    assertTrue(ab._bb.isDirect());
  }

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
  public void testNameOfClass() {

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

  @Test
  public void testSerializeBootstrapFreezable() {
    MyCustomBootstrapFreezable freezable = new MyCustomBootstrapFreezable("payload42");
    byte[] bytes = AutoBuffer.serializeBootstrapFreezable(freezable);
    assertNotNull(bytes);
    byte[] expectedPayload = freezable._data.getBytes();
    byte[] expectedMessage = ArrayUtils.append(
            new byte[]{
                    0, // marker
                    (byte) (freezable.frozenType() + 1), // +1 because of compressed integer coding, see AutoBuffer#put1
                    (byte) (expectedPayload.length + 1)  // ditto
            },
            expectedPayload
    );
    assertArrayEquals(expectedMessage, bytes);
  }

  @Test
  public void testDeserializeBootstrapFreezable() {
    int typeId = new MyCustomBootstrapFreezable("anything").frozenType();
    String data = "my test data 42"; 
    byte[] payload = data.getBytes();
    byte[] bytes = ArrayUtils.append(
            new byte[]{
                    0, // marker
                    (byte) (typeId + 1),         // +1 because of compressed integer coding, see AutoBuffer#put1
                    (byte) (payload.length + 1)  // ditto
            },
            payload
    );
    MyCustomBootstrapFreezable freezable = (MyCustomBootstrapFreezable) AutoBuffer.deserializeBootstrapFreezable(bytes);
    assertEquals(data, freezable._data);
  }

  @Test
  public void testMaliciousFreezable() {
    int typeId = TypeMap.onIce(MaliciousFreezable.class.getName());
    byte[] payload = "any data - it doesn't matter what is here".getBytes();
    byte[] bytes = ArrayUtils.append(
            new byte[]{
                    0, // marker
                    (byte) (typeId + 1),         // +1 because of compressed integer coding, see AutoBuffer#put1
                    (byte) (payload.length + 1)  // ditto
            },
            payload
    );
    IllegalStateException ise = null;
    try {
      AutoBuffer.deserializeBootstrapFreezable(bytes);
    } catch (IllegalStateException e) {
      ise = e;
    } finally {
      assertEquals("we are safe", MaliciousFreezable.GLOBAL_STATE); // global state was not modified
    }
    assertNotNull(ise);
    assertEquals(
            "Class with frozenType=" + typeId + " cannot be deserialized because it is not part of the TypeMap.",
            ise.getMessage()
    );
  }

  public static class MyCustomBootstrapFreezable 
          extends Iced<MyCustomBootstrapFreezable> implements BootstrapFreezable<MyCustomBootstrapFreezable> {
    private final String _data;

    MyCustomBootstrapFreezable(String data) {
      _data = data;
    }
  }

  public static class MaliciousFreezable implements Freezable<MaliciousFreezable> {
    public static String GLOBAL_STATE = "we are safe";
    @Override
    public AutoBuffer write(AutoBuffer ab) {
      GLOBAL_STATE = "hacked!";
      return null;
    }

    @Override
    public MaliciousFreezable read(AutoBuffer ab) {
      GLOBAL_STATE = "hacked!";
      return null;
    }

    @Override
    public AutoBuffer writeJSON(AutoBuffer ab) {
      GLOBAL_STATE = "hacked!";
      return null;
    }

    @Override
    public MaliciousFreezable readJSON(AutoBuffer ab) {
      GLOBAL_STATE = "hacked!";
      return null;
    }

    @Override
    public int frozenType() {
      GLOBAL_STATE = "hacked!";
      return 0;
    }

    @Override
    public byte[] asBytes() {
      GLOBAL_STATE = "hacked!";
      return new byte[0];
    }

    @Override
    public MaliciousFreezable reloadFromBytes(byte[] ary) {
      GLOBAL_STATE = "hacked!";
      return null;
    }

    @Override
    public MaliciousFreezable clone() {
      GLOBAL_STATE = "hacked!";
      return null;
    }
  }

  public static class MyCustomTypeMapExtension implements TypeMapExtension {

    @Override
    public String[] getBoostrapClasses() {
      return new String[]{MyCustomBootstrapFreezable.class.getName()};
    }
  }
  
}
