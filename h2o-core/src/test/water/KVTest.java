package water;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.*;

public class KVTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(2); }

  // ---
  // Run some basic tests.  Create a key, test that it does not exist, insert a
  // value for it, get the value for it, delete it.
  @Test public void testBasicCRUD() {
    System.out.println("Hello test!!!");
    Key k1 = Key.make("key1");
    Value v0 = DKV.get(k1);
    assertNull(v0);
    Value v1 = new Value(k1,"test0 bits for Value");
    DKV.put(k1,v1);
    assertEquals(v1._key,k1);
    Value v2 = DKV.get(k1);
    assertEquals(v1,v2);
    DKV.remove(k1);
    Value v3 = DKV.get(k1);
    assertNull(v3);
  }

  // ---
  // Make 100 keys, verify them all, delete them all.
  @Test public void test100Keys() {
    Key   keys[] = new Key  [100];
    Value vals[] = new Value[keys.length];
    for( int i=0; i<keys.length; i++ ) {
      Key k = keys[i] = Key.make("key"+i);
      Value v0 = DKV.get(k);
      assertNull(v0);
      Value v1 = vals[i] = new Value(k,"test2 bits for Value"+i);
      DKV.put(k,v1);
      assertEquals(v1._key,k);
    }
    for( int i=0; i<keys.length; i++ ) {
      Value v = DKV.get(keys[i]);
      assertEquals(vals[i],v);
    }
    for( int i=0; i<keys.length; i++ ) {
      DKV.remove(keys[i]);
    }
    for( int i=0; i<keys.length; i++ ) {
      Value v3 = DKV.get(keys[i]);
      assertNull(v3);
    }
  }

  // ---
  // Issue a slew of remote puts, then issue a DFJ job on the array of keys.
//  @Test public void testRemoteBitSet() throws Exception {
//    // Issue a slew of remote key puts
//    Key[] keys = new Key[32];
//    for( int i = 0; i < keys.length; ++i ) {
//      Key k = keys[i] = Key.make("key"+i);
//      byte[] bits = new byte[4];
//      bits[0] = (byte)i;        // Each value holds a shift-count
//      Value val = new Value(k,bits);
//      DKV.put(k,val);
//    }
//    DKV.write_barrier();
//
//    RemoteBitSet r = new RemoteBitSet();
//    r.invoke(keys);
//    assertEquals((int)((1L<<keys.length)-1), r._x);
//    for( Key k : keys ) DKV.remove(k);
//  }
//
//  // Remote Bit Set: OR together the result of a single bit-mask where the
//  // shift-amount is passed in in the Key.
//  public static class RemoteBitSet extends MRTask {
//    private int _x;
//    public void map( Key key ) { _x = 1<<(DKV.get(key).memOrLoad()[0]); }
//    public void reduce( DRemoteTask rbs ) { _x |= ((RemoteBitSet)rbs)._x; }
//  }
//
//  // ---
//  // Issue a large Key/Value put/get - testing the TCP path
//  @Test public void testTcpCRUD() {
//    // Make an execution key homed to the remote node
//    H2O cloud = H2O.CLOUD;
//    H2ONode target = cloud._memary[0];
//    if( target == H2O.SELF ) target = cloud._memary[1];
//    Key remote_key = Key.make("test4_remote",(byte)1,Key.DFJ_INTERNAL_USER,target); // A key homed to a specific target
//    Value v0 = DKV.get(remote_key);
//    assertNull(v0);
//    // It's a Big Value
//    byte[] bits = new byte[100000];
//    for( int i=0; i<bits.length; i++ )
//      bits[i] = (byte)i;
//    Value v1 = new Value(remote_key,bits);
//    // Start the remote-put operation
//    DKV.put(remote_key,v1);
//    assertEquals(v1._key,remote_key);
//    Value v2 = DKV.get(remote_key);
//    assertEquals(v1,v2);
//    DKV.remove(remote_key);
//    Value v3 = DKV.get(remote_key);
//    assertNull(v3);
//  }
//
//
//  // ---
//  // Map in h2o.jar - a multi-megabyte file - into Arraylets.
//  // Run a distributed byte histogram.
//  @Test public void testMultiMbFile() throws Exception {
//    File file = find_test_file("target/h2o.jar");
//    Key h2okey = load_test_file(file);
//    ByteHisto bh = new ByteHisto();
//    bh.invoke(h2okey);
//    int sum=0;
//    for( int i=0; i<bh._x.length; i++ )
//      sum += bh._x[i];
//    assertEquals(file.length(),sum);
//  
//    Lockable.delete(h2okey);
//  }
//  
//  // Byte-wise histogram
//  public static class ByteHisto extends MRTask {
//    int[] _x;
//    // Count occurrences of bytes
//    public void map( Key key ) {
//      _x = new int[256];        // One-time set histogram array
//      Value val = DKV.get(key); // Get the Value for the Key
//      byte[] bits = val.memOrLoad();  // Compute local histogram
//      for( int i=0; i<bits.length; i++ )
//        _x[bits[i]&0xFF]++;
//    }
//    // ADD together all results
//    public void reduce( DRemoteTask rbs ) {
//      ByteHisto bh = (ByteHisto)rbs;
//      if( _x == null ) { _x = bh._x; return; }
//      for( int i=0; i<_x.length; i++ )
//        _x[i] += bh._x[i];
//    }
//  }
//
//  // ---
//  // Run an atomic function remotely, one time only
//  @Test public void testRemoteAtomic() {
//    // Make an execution key homed to the remote node
//    H2O cloud = H2O.CLOUD;
//    H2ONode target = cloud._memary[0];
//    if( target == H2O.SELF ) target = cloud._memary[1];
//    Key key = Key.make("test6_remote",(byte)1,Key.DFJ_INTERNAL_USER,target);
//    // It's a plain empty byte array - but too big for atomic update on purpose
//    Value v1 = new Value(key,16);
//    // Remote-put operation
//    DKV.put(key,v1);
//    DKV.write_barrier();
//  
//    // Atomically run this function on a clone of the bits from the existing
//    // Key and install the result as the new Value.  This function may run
//    // multiple times if there are collisions.
//    Atomic q = new Atomic2();
//    q.invoke(key);              // Run remotely; block till done
//    Value val3 = DKV.get(key);
//    assertNotSame(v1,val3);
//    AutoBuffer ab = new AutoBuffer(val3.memOrLoad());
//    assertEquals(2,ab.get8(0));
//    assertEquals(2,ab.get8(8));
//    DKV.remove(key);            // Cleanup after test
//  }
//  
//  public static class Atomic2 extends Atomic {
//    @Override public Value atomic( Value val ) {
//      byte[] bits1 = val.memOrLoad();
//      long l1 = UDP.get8(bits1,0);
//      long l2 = UDP.get8(bits1,8);
//      l1 += 2;
//      l2 += 2;
//      byte[] bits2 = new byte[16];
//      UDP.set8(bits2,0,l1);
//      UDP.set8(bits2,8,l2);
//      return new Value(_key,bits2);
//    }
//  }
//  
//  // ---
//  // Test parsing "cars.csv" and running LinearRegression
//  @Test public void testLinearRegression() {
//    Key fkey = load_test_file("smalldata/cars.csv");
//    Key okey = Key.make("cars.hex");
//    ParseDataset.parse(okey,new Key[]{fkey});
//    ValueArray va = DKV.get(okey).get();
//    // Compute LinearRegression between columns 2 & 3
//    JsonObject res = LinearRegression.run(va,2,3);
//    assertEquals( 58.326241377521995, res.get("Beta1"      ).getAsDouble(), 0.000001);
//    assertEquals(-124.57816399564385, res.get("Beta0"      ).getAsDouble(), 0.000001);
//    assertEquals( 0.9058985668996267, res.get("RSquared"   ).getAsDouble(), 0.000001);
//    assertEquals( 0.9352584499359637, res.get("Beta1StdErr").getAsDouble(), 0.000001);
//    va.delete();
//  }
}
