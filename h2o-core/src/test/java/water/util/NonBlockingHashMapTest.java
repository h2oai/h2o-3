package water.util;

import org.junit.Ignore;
import org.junit.Test;
import water.nbhm.NonBlockingHashMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class NonBlockingHashMapTest {

  /**
   * Demonstrates that NonBlockingHashMap#putIfAbsent doesn't guarantee
   * the key of a value won't actually change over time or even that
   * the thread that inserted the value will be the one who's key will
   * be preserved.
   * 
   * @throws Exception not expected
   */
  @Test 
  @Ignore // doesn't work well on CI, can be used locally to demonstrate the behavior
  public void testPutIfAbsent_PubDev6319() throws Exception {
    NonBlockingHashMap<K, K> map = new NonBlockingHashMap<>();
    
    Putter[] putters = new Putter[4];
    
    for (int i = 0; i < putters.length; i++) {
      putters[i] = new Putter(map);
      putters[i].start();
    }

    int totalInserts = 0;
    int totalForeignKeys = 0;
    int totalForeignValues = 0;
    
    for (Putter putter : putters) {
      putter.join();
      System.out.println(putter.toString());
      totalInserts += putter.nInserts;
      totalForeignKeys += putter.nForeignKeys;
      totalForeignValues += putter.nForeignValues;
    }

    assertNotEquals(0, map.size());       // sanity check - something was inserted
    assertEquals(map.size(), totalInserts);          // size of the map should be the same as the aggregated number of inserts
    assertEquals(0, totalForeignValues);    // values should be inserted only once and shouldn't change over time
    assertNotEquals(0, totalForeignKeys); // we have no guarantee whose key will be used - it doesn't have to
                                                     // be the thread that actually made the insert - this causes PUBDEV-6319
  }
 
  private static class Putter extends Thread {
    NonBlockingHashMap<K, K> map;
    
    int nInserts;
    int nForeignKeys;
    int nForeignValues;

    Putter(NonBlockingHashMap<K, K> map) {
      this.map = map;
    }

    @Override
    public void run() {
      for (int i = 0; i < 100000; i++) {
        K k = new K(System.nanoTime(), this);
        K old = map.putIfAbsent(k, k);
        if (old == null) {
          nInserts++;
          if (map.getk(k) != k)
            nForeignKeys++;
          if (map.get(k).p != k.p || map.get(k) != k)
            nForeignValues++;
        }
      }
    }

    @Override
    public String toString() {
      return "Putter{" +
              "nInserts=" + nInserts +
              ", nForeignKeys=" + nForeignKeys +
              ", nForeignValues=" + nForeignValues +
              '}';
    }
  }
  
  private static class K {
    private final long k;
    private final Putter p;

    public K(long k, Putter p) {
      this.k = k;
      this.p = p;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      K k1 = (K) o;

      return k == k1.k;
    }

    @Override
    public int hashCode() {
      return (int) (k ^ (k >>> 32));
    }

    @Override
    public String toString() {
      return "K{" +
              "k=" + k +
              '}';
    }
  }
  
}
