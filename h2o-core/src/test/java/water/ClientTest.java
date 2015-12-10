package water;

import static org.junit.Assert.*;
import org.junit.*;
import water.fvec.Chunk;
import water.fvec.Frame;

public class ClientTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(3); }
  
  // ---
  // Run some basic tests.  Create a key, test that it does not exist, insert a
  // value for it, get the value for it, delete it.
  @Test public void testBasicCRUD() {
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

  // Speed test: takes my lappy 4msec per iteration over covtype
  @Test public void biggerTest() {
    // Do not run this test on non-client nodes, it is too slow
    if( !H2O.ARGS.client ) return;

    // For a client node, start the test & then commit suicide.
    // The goal is to check for errors on the server-side
    new Thread() {
      @Override public void run() {
        System.out.println("Client is starting timer");
        try { Thread.sleep(2000); } catch(Exception ignore) {}
        System.out.println("Client is committing shutting prematurely");
        System.exit(0);
      }
    }.start();
    System.out.println("Server is loading file");

    Frame fr = RPC.call(H2O.CLOUD.leader(),new DTask() {
        Frame _fr;              // Output frame
        @Override
        public void compute2() {
          _fr = parse_test_file(Key.make("covtype.hex"),"../../datasets/UCI/UCI-large/covtype/covtype.data");
          tryComplete();
        } 
      }).get()._fr;
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
      for( int i=0; i<xs._len; i++ ) {
        double X = xs.atd(i);  double Y = ys.atd(i);
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
