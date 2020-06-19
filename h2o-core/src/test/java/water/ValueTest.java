package water;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ValueTest {

  @Test
  public void testReloadFromBytes() {
    Key<MyKeyed> k = Key.make();
    try {
      MyKeyed mk = new MyKeyed(k, 42);
      DKV.put(mk);

      Value v = DKV.get(k);
      assertNotNull(v);
      v.freePOJO();
      assertNull(v.rawPOJO());

      MyKeyed mk2 = v.get();
      assertNotNull(mk2._captured_bytes);
      assertNotSame(mk, mk2);
      assertNotSame(mk2.asBytes(), mk2._captured_bytes);
      assertArrayEquals(mk2.asBytes(), mk2._captured_bytes);
    } finally {
      DKV.remove(k);
    }
  }

  @Test
  public void testReloadFromBytesMemKeyed() {
    Key<MyMemKeyed> k = Key.make();
    try {
      MyMemKeyed mk = new MyMemKeyed(k, 42);
      DKV.put(mk);

      assertEquals(42, mk.value());
      
      Value v = DKV.get(k);
      assertNotNull(v);
      assertSame(mk.asBytes(), v.rawMem());

      v.freePOJO();
      assertNull(v.rawPOJO());

      MyMemKeyed mk2 = v.get();
      assertNotSame(mk, mk2);
      assertSame(mk2.asBytes(), v.rawMem());
    } finally {
      DKV.remove(k);
    }
  }

  private static class MyKeyed extends Keyed<MyKeyed> {
    int _value;
    transient byte[] _captured_bytes;
    MyKeyed(Key<MyKeyed> key, int value) {
      super(key);
      _value = value;
    }

    @Override
    public MyKeyed reloadFromBytes(byte[] ary) {
      _captured_bytes = ary;
      return super.reloadFromBytes(ary);
    }
  }

  private static class MyMemKeyed extends MemKeyed<MyMemKeyed> {

    MyMemKeyed(Key<MyMemKeyed> key, int value) {
      super(key, ByteBuffer.allocate(4).putInt(value).array());
    }

    int value() {
      return ByteBuffer.wrap(_mem).getInt();
    }

  }

}
