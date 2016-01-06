package water.nbhm;

import org.junit.Ignore;
import org.junit.Test;

public class NonBlockingHashMapSizeLimitTest {
  @Ignore
  @Test public void run() {
    NonBlockingHashMap<Object, Object> nbhm = new NonBlockingHashMap();
    long count=0;
    do {
      count++;
      nbhm.put(new Object(), "");
      if (count % 1000000 == 0) System.err.println(count);
    } while(true);
  }
}
