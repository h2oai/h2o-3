package water;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.File;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.util.UnsafeUtils;

public class KVTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(3); }

  // ---
  // Run some basic tests.  Create a key, test that it does not exist, insert a
  // value for it, get the value for it, delete it.
  @Test public void testBasicCRUD() {
    long start = System.currentTimeMillis();
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
    System.out.println("BasicCrud "+(System.currentTimeMillis()-start));
  }

  // ---
  // Make 100 keys, verify them all, delete them all.
  @Test public void test100Keys() {
    long start = System.currentTimeMillis();
    Futures fs = new Futures();
    Key   keys[] = new Key  [100];
    Value vals[] = new Value[keys.length];
    for( int i=0; i<keys.length; i++ ) {
      Key k = keys[i] = Key.make("key"+i);
      Value v0 = DKV.get(k);
      assertNull(v0);
      Value v1 = vals[i] = new Value(k,"test2 bits for Value"+i);
      DKV.put(k,v1,fs);
      assertEquals(v1._key,k);
    }
    for( int i=0; i<keys.length; i++ ) {
      Value v = DKV.get(keys[i]);
      assertEquals(vals[i],v);
    }
    for( Key key : keys )
      DKV.remove(key,fs);
    for( Key key : keys ) {
      Value v3 = DKV.get(key);
      assertNull(v3);
    }
    fs.blockForPending();
    System.out.println("100Keys "+(System.currentTimeMillis()-start));
  }

  // ---
  // Issue a slew of remote puts, then issue a DFJ job on the array of keys.
  @Test public void testRemoteBitSet() throws Exception {
    long start = System.currentTimeMillis(); 
    Futures fs = new Futures();
    // Issue a slew of remote key puts
    Key[] keys = new Key[32];
    for( int i = 0; i < keys.length; ++i ) {
      Key k = keys[i] = Key.make("key"+i);
      byte[] bits = new byte[4];
      bits[0] = (byte)i;        // Each value holds a shift-count
      Value val = new Value(k,bits);
      DKV.put(k,val,fs);
    }
    fs.blockForPending();

    int x = new RemoteBitSet().doAll(keys)._x;
    assertEquals((int)((1L<<keys.length)-1), x);
    for( Key k : keys ) DKV.remove(k,fs);
    fs.blockForPending();
    System.out.println("RemoteBitSet "+(System.currentTimeMillis()-start));
  }

  // Remote Bit Set: OR together the result of a single bit-mask where the
  // shift-amount is passed in in the Key.
  public static class RemoteBitSet extends MRTask<RemoteBitSet> {
    private int _x;
    @Override public void map( Key key ) { _x = 1<<(DKV.get(key).memOrLoad()[0]); }
    @Override public void reduce( RemoteBitSet rbs ) { _x |= rbs._x; }
  }

  // ---
  // Issue a large Key/Value put/get - testing the TCP path
  @Test public void testTcpCRUD() {
    long start = System.currentTimeMillis();
    // Make an execution key homed to the remote node
    H2O cloud = H2O.CLOUD;
    H2ONode target = cloud._memary[0];
    if( target == H2O.SELF ) target = cloud._memary[1];
    Key remote_key = Key.make("test4_remote",(byte)1,Key.BUILT_IN_KEY,true,target); // A key homed to a specific target
    Value v0 = DKV.get(remote_key);
    assertNull(v0);
    // It's a Big Value
    byte[] bits = new byte[100000];
    for( int i=0; i<bits.length; i++ )
      bits[i] = (byte)i;
    Value v1 = new Value(remote_key,bits);
    // Start the remote-put operation
    DKV.put(remote_key,v1);
    assertEquals(v1._key,remote_key);
    Value v2 = DKV.get(remote_key);
    assertEquals(v1,v2);
    DKV.remove(remote_key);
    Value v3 = DKV.get(remote_key);
    assertNull(v3);
    System.out.println("TcpCRUD "+(System.currentTimeMillis()-start));
  }


  // ---
  // Map in h2o.jar - a multi-megabyte file - into a NFSFileVec
  // Run a distributed byte histogram.
  @Test public void testMultiMbFile() {
    long start = System.currentTimeMillis();
    NFSFileVec nfs = null;
    try {
      File file = find_test_file("build/h2o-core.jar");
      if( file == null ) return;  // Nothing to test
      // Return a Key mapping to a NFSFileVec over the file
      nfs = NFSFileVec.make(file);
      ByteHisto bh = new ByteHisto().doAll(nfs);
      int sum = water.util.ArrayUtils.sum(bh._x);
      assertEquals(file.length(),sum);
    } finally {
      if( nfs != null ) nfs.remove(); // remove from DKV
    }
    System.out.println("MultiMbFile "+(System.currentTimeMillis()-start));
  }
  
  // Byte-wise histogram
  public static class ByteHisto extends MRTask<ByteHisto> {
    int[] _x;
    // Count occurrences of bytes
    @Override public void map( Chunk chk ) {
      _x = new int[256];        // One-time set histogram array
      byte[] bits = chk.getBytes(); // Raw file bytes
      for( byte b : bits ) // Compute local histogram
        _x[b&0xFF]++;
    }
    // ADD together all results
    @Override public void reduce( ByteHisto bh ) { water.util.ArrayUtils.add(_x,bh._x); }
  }

  // ---
  // Run an atomic function remotely, one time only
  @Test public void testRemoteAtomic() {
    long start = System.currentTimeMillis();
    // Make an execution key homed to the remote node
    H2O cloud = H2O.CLOUD;
    H2ONode target = cloud._memary[0];
    if( target == H2O.SELF ) target = cloud._memary[1];
    Key key = Key.make("test6_remote",(byte)1,Key.BUILT_IN_KEY,true,target);
    // It's a plain empty byte array - but too big for atomic update on purpose
    Value v1 = new Value(key,new byte[16]);
    // Remote-put operation
    DKV.put(key,v1);
    DKV.write_barrier();
  
    // Atomically run this function on a clone of the bits from the existing
    // Key and install the result as the new Value.  This function may run
    // multiple times if there are collisions.
    Atomic q = new Atomic2();
    q.invoke(key);              // Run remotely; block till done
    Value val3 = DKV.get(key);
    assertNotSame(v1,val3);
    AutoBuffer ab = new AutoBuffer(val3.memOrLoad());
    assertEquals(2,ab.get8(0));
    assertEquals(2,ab.get8(8));
    DKV.remove(key);            // Cleanup after test
    System.out.println("RemoteAtomic "+(System.currentTimeMillis()-start));
  }
  
  public static class Atomic2 extends Atomic {
    @Override public Value atomic( Value val ) {
      byte[] bits1 = val.memOrLoad();
      long l1 = UnsafeUtils.get8(bits1, 0);
      long l2 = UnsafeUtils.get8(bits1,8);
      l1 += 2;
      l2 += 2;
      byte[] bits2 = new byte[16];
      UnsafeUtils.set8(bits2,0,l1);
      UnsafeUtils.set8(bits2,8,l2);
      return new Value(_key,bits2);
    }
  }
  
  // Speed test: takes my lappy 4msec per iteration over covtype
  @Test public void biggerTest() {
    // Do not run this test on non-client nodes, it is too slow
    if( !H2O.ARGS.client ) return;
    // For a client node, start the test & then commit suicide.
    // The goal is to check for errors on the server-side
    new Thread() { 
      @Override public void run() {
        System.out.println("Client is starting timer");
        try { Thread.sleep(8000); } catch(Exception ignore) {}
        System.out.println("Client is committing shutting prematurely");
        System.exit(0);
      }
    }.start();
    System.out.println("Server is loading file");
    Frame fr = parse_test_file("../../datasets/UCI/UCI-large/covtype/covtype.data");
    System.out.println("Server loaded file");
    try {
      final int iters = 100;
      final long start = System.currentTimeMillis();
      CalcSumsTask lr1 = null;
      for( int i=0; i<iters; i++ ) {
        lr1 = new CalcSumsTask().doAll(fr.vecs()[0],fr.vecs()[1]);
      }
      final long end = System.currentTimeMillis();
      final double meanX = lr1._sumX/lr1._nrows;
      final double meanY = lr1._sumY/lr1._nrows;
      System.out.println("CalcSums iter over covtype: "+(end-start)/iters+"ms, meanX="+meanX+", meanY="+meanY+", nrows="+lr1._nrows);
    } finally {
      fr.delete();
    }
  }
  public static class CalcSumsTask extends MRTask<CalcSumsTask> {
    long _nrows; // Rows used
    double _sumX,_sumY,_sumX2; // Sum of X's, Y's, X^2's
    @Override public void map( Chunk xs, Chunk ys ) {
      for( int i=0; i<xs.len(); i++ ) {
        double X = xs.at0(i);  double Y = ys.at0(i);
        if( !Double.isNaN(X) && !Double.isNaN(Y)) {
          _sumX += X;    _sumY += Y;
          _sumX2+= X*X;  _nrows++;
        }
      }
    }
    @Override public void reduce( CalcSumsTask lr1 ) {
      _sumX += lr1._sumX ;  _sumY += lr1._sumY ;
      _sumX2+= lr1._sumX2;  _nrows += lr1._nrows;
    }
  }

}
