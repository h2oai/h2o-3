package water;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.util.IcedInt;
import water.util.IcedInt.AtomicIncrementAndGet;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

/**
 * Created by tomasnykodym on 10/5/15.
 *
 * Test that our DKV follows java mm.
 */
public class DKVTest extends TestUtil {
  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }


  private static class TestMM extends MRTask<TestMM> {
    final Key [] _keys;

    public TestMM(Key[] keys) {_keys = keys;}

    @Override public void setupLocal() {
      final H2OCountedCompleter barrier = (new H2OCountedCompleter() {
        @Override
        public void compute2() {
        }
      });
      barrier.addToPendingCount(_keys.length-1);
      for(Key k:_keys)DKV.get(k);
      for (int i = 0; i < _keys.length; ++i) {
        final Key fk = _keys[i];
        new AtomicIncrementAndGet(new H2OCallback<AtomicIncrementAndGet>(barrier) {
          @Override
          public void callback(AtomicIncrementAndGet f) {
            final int lb = f._val;
            for(H2ONode node:H2O.CLOUD._memary) {
              if(node != H2O.SELF) {
                barrier.addToPendingCount(1);
                new RPC(node,new DTask() {
                  @Override
                  public void compute2() {
                    IcedInt val = DKV.getGet(fk);
                    if (val._val < lb)
                      throw new IllegalArgumentException("DKV seems to be in inconsistent state after TAtomic, value lower than expected, expected at least " + lb + ", got " + val._val);
                    tryComplete();
                  }
                }).addCompleter(barrier).call();
              }
            }
          }
        }).fork(_keys[i]);
      }
      barrier.join();
    }
  }

  /**
   * Test for recently discovered bug in DKV where updates sometimes failed to wait for all invalidates to the given key.
   *
   * Test that DKV puts (Tatomic updates) are globally visible after the tatomic task gets back.
   *
   * Makes a Key per node, caches it on all other nodes and then performs atomic updates followed by global visibility check.
   * Fails if the update is not globally visible.
   * 
   */
  @Test
  public  void testTatomic(){
    try {
      final Key[] keys = new Key[H2O.CLOUD.size()];
      for (int r = 0; r < 20; ++r) {
        System.out.println("iteration " + r);
        try {
          for (int i = 0; i < keys.length; ++i) // byte rf, byte systemType, boolean hint, H2ONode... replicas
            DKV.put(keys[i] = Key.make((byte) 1, Key.HIDDEN_USER_KEY, true, H2O.CLOUD._memary[i]), new IcedInt(0));
          new TestMM(keys).doAllNodes();
        } finally {
          for (Key k : keys)
            if (k != null) DKV.remove(k);
        }
      }
    } finally {
//      H2O.orderlyShutdown();
    }
  }

  class Bytes extends Iced<Bytes> {
    public byte[] _b;
    Bytes(byte[] b) { _b = b; }
  }

  @Ignore // This currently fails if the driver node owns the Key, as the live POJO version in the DKV is shared with the object that was inserted...
  @Test
  public  void testConsistency(){
    // make a random key
    Key k1 = Key.make();
    try {
      // create random bytes
      byte[] b1 = new byte[100];
      new Random(1234).nextBytes(b1);
      Bytes bytes1 = new Bytes(b1);

      // put them into the DKV
      DKV.put(k1, bytes1);

      // overwrite the state of the live object
      new Random(4321).nextBytes(bytes1._b);

      // check that the DKV version isn't modified (i.e., that the DKV has the "original" version that was put into the store)
      Assert.assertFalse(Arrays.equals(((Bytes) DKV.getGet(k1))._b, bytes1._b));
    } finally {
      DKV.remove(k1);
    }
  }

}
