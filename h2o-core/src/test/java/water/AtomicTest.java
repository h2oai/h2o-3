package water;

import org.junit.*;

import java.util.Arrays;
import java.util.Random;

public class AtomicTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(3); }

  public Key makeKey(String n, boolean remote) {
    if(!remote) return Key.make(n);
    H2O cloud = H2O.CLOUD;
    H2ONode target = cloud._memary[0];
    if( target == H2O.SELF ) target = cloud._memary[1];
    return Key.make(n,(byte)1,Key.BUILT_IN_KEY,true,target);
  }

  // Simple wrapper class defining an array-of-keys that is serializable.
  private static class Ary extends Iced {
    public final Key[] _keys;
    Ary( Key[] keys ) { _keys = keys; }
  }

  private static class Append {
    static private void append(Key keys, final Key k) {
      new Atomic() {
        @Override public Value atomic(Value val) {
          Ary ks = val == null ? new Ary(new Key[0]) : (Ary)val.get();
          Key[] keys = Arrays.copyOf(ks._keys,ks._keys.length+1);
          keys[keys.length-1]=k;
          return new Value(_key,new Ary(keys));
        }
      }.invoke(keys);
    }
  }

  private void doBasic(Key k) {
    Value v = DKV.get(k);
    Assert.assertNull(v);

    Key a1 = Key.make("tatomic 1");
    Append.append(k,a1);
    Key[] ks = new AutoBuffer(DKV.get(k).memOrLoad()).getA(Key.class);
    Assert.assertEquals(1, ks.length);
    Assert.assertEquals(a1, ks[0]);

    Key a2 = Key.make("tatomic 2");
    Append.append(k,a2);
    ks = new AutoBuffer(DKV.get(k).memOrLoad()).getA(Key.class);
    Assert.assertEquals(2, ks.length);
    Assert.assertEquals(a1, ks[0]);
    Assert.assertEquals(a2, ks[1]);
    DKV.remove(k);
  }

  @Test public void testBasic() {
    doBasic(makeKey("basic", false));
  }

  @Test public void testBasicRemote() {
    doBasic(makeKey("basicRemote", true));
  }

  private void doLarge(Key k) {
    Value v = DKV.get(k);
    Assert.assertNull(v);

    Random r = new Random(1234567890123456789L);
    int total = 0;
    while( total < H2O.ARGS.MTU*8 ) {
      byte[] kb = new byte[Key.KEY_LENGTH];
      r.nextBytes(kb);
      Key nk = Key.make(kb);
      Append.append(k,nk);
      v = DKV.get(k);
      byte[] vb = v.memOrLoad();
      Assert.assertArrayEquals(kb, Arrays.copyOfRange(vb, vb.length-kb.length, vb.length));
      total = vb.length;
    }
    DKV.remove(k);
  }


  @Test public void testLarge() {
    doLarge(makeKey("large", false));
  }

  @Test public void testLargeRemote() {
    doLarge(makeKey("largeRemote", true));
  }
}
