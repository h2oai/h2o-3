package water.nbhm;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Eventually we will have to cover this class with tests
 * Created by vpatryshev on 1/19/17.
 */
public class NonBlockingHashMapTest {

  @Test
  public void testKvs() throws Exception {

  }

  @Test
  public void testPrint() throws Exception {

  }

  @Test
  public void testReprobes() throws Exception {

  }

  @Test
  public void testInitialize() throws Exception {

  }

  @Test
  public void testSize() throws Exception {

  }

  @Test
  public void testIsEmpty() throws Exception {

  }

  @Test
  public void testContainsKey() throws Exception {

  }

  @Test
  public void testContains() throws Exception {

  }

  @Test
  public void testPut() throws Exception {

  }

  @Test
  public void testPutIfAbsent() throws Exception {

  }

  @Test
  public void testRemove() throws Exception {

  }

  @Test
  public void testRemove1() throws Exception {

  }

  @Test
  public void testReplace() throws Exception {

  }

  @Test
  public void testReplace1() throws Exception {

  }

  @Test
  public void testPutIfMatchUnlocked() throws Exception {

  }

  @Test
  public void testPutIfMatch() throws Exception {

  }

  @Test
  public void testPutAll() throws Exception {

  }

  @Test
  public void testClear() throws Exception {

  }

  @Test
  public void testContainsValue() throws Exception {

  }

  @Test
  public void testRehash() throws Exception {

  }

  @Test
  public void testClone() throws Exception {

  }

  @Test
  public void testToString() throws Exception {

  }

  @Test
  public void testGet() throws Exception {

  }

  @Test
  public void testGetk() throws Exception {

  }

  @Test
  public void testRaw_array() throws Exception {

  }

  @Test
  public void testElements() throws Exception {

  }

  @Test
  public void testValues() throws Exception {

  }

  @Test
  public void testKeys() throws Exception {

  }

  @Test
  public void testKeySet() throws Exception {

  }

  @Test
  public void testEntrySet() throws Exception {

  }

  @Test
  public void testReadOnly_no_concurrency() throws Exception {
    NonBlockingHashMap<String, Byte> sut = new NonBlockingHashMap<String, Byte>();
    sut.put("CA", (byte)0xca);
    sut.readOnly();
    assertEquals((byte)0xca, sut.get("CA").byteValue());
    // this case crashes right here, because Cliff does not grok generics. Period.
    Byte actual1 = sut.putIfAbsent("FE", (byte)0xfe);
    assertEquals(NonBlockingHashMap.READONLY, sut.putIfMatch("DE", (byte)0xde, (byte)0xde));
    assertEquals(NonBlockingHashMap.READONLY, sut.putIfMatch("DE", (byte)0x0e, (byte)0xde));
    try {
      sut.put("Ad", (byte)0xad);
      fail("We were supposed to throw an IllegalStateException");
    } catch (IllegalStateException iae) {
      // as expected
    }
  }
  @Test
  public void testReadOnly() throws Exception {

  }
}