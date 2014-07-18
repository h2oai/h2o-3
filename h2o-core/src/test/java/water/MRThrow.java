package water;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.*;

import java.io.File;
import java.util.concurrent.ExecutionException;
import jsr166y.CountedCompleter;
import water.fvec.Chunk;
import water.fvec.NFSFileVec;

@Test(groups={"multi-node"})
public class MRThrow extends TestUtil {
  MRThrow() { super(2); }

  // ---
  // Map in h2o.jar - a multi-megabyte file - into Arraylets.
  // Run a distributed byte histogram.  Throw an exception in *some* map call,
  // and make sure it's forwarded to the invoke.
  @Test public void testInvokeThrow() {
    File file = find_test_file("h2o-core/build/h2o-core.jar");
    assertTrue( "Missing test file; do a 'make' and retest", file != null);
    // Return a Key mapping to a NFSFileVec over the file
    NFSFileVec nfs = NFSFileVec.make(file);
    try {
      for(int i = 0; i < H2O.CLOUD._memary.length; ++i){
        ByteHistoThrow bh = new ByteHistoThrow(H2O.CLOUD._memary[i]);
        try {
          bh.doAll(nfs); // invoke should throw DistributedException wrapped up in RunTimeException
          fail("should've thrown");
        } catch( RuntimeException e ) {
          assertTrue(e.getMessage().contains("test"));
        } catch( Throwable ex ) {
          ex.printStackTrace();
          fail("Expected RuntimeException, got " + ex.toString());
        }
      }
    } finally {
      if( nfs != null ) nfs.remove(); // remove from DKV
    }
  }

  @Test public void testGetThrow() {
    File file = find_test_file("h2o-core/build/h2o-core.jar");
    assertTrue( "Missing test file; do a 'make' and retest", file != null);
    NFSFileVec nfs = NFSFileVec.make(file);
    try {
      for(int i = 0; i < H2O.CLOUD._memary.length; ++i){
        ByteHistoThrow bh = new ByteHistoThrow(H2O.CLOUD._memary[i]);
        try {
          bh.doAll(nfs); // invoke should throw DistributedException wrapped up in RunTimeException
          fail("should've thrown");
        } catch( DException.DistributedException e ) {
          assertTrue(e.getMessage().contains("test"));
        } catch(Throwable ex) {
          ex.printStackTrace();
          fail("Expected ExecutionException, got " + ex.toString());
        }
      }
    } finally {
      if( nfs != null ) nfs.remove(); // remove from DKV
    }
  }

  @Test public void testContinuationThrow() throws InterruptedException, ExecutionException {
    File file = find_test_file("h2o-core/build/h2o-core.jar");
    assertTrue( "Missing test file; do a 'make' and retest", file != null);
    NFSFileVec nfs = NFSFileVec.make(file);
    try {
      for(int i = 0; i < H2O.CLOUD._memary.length; ++i){
        final ByteHistoThrow bh = new ByteHistoThrow(H2O.CLOUD._memary[i]);
        final boolean [] ok = new boolean[]{false};
        try {
          bh.setCompleter(new CountedCompleter() {
              @Override public void compute() { tryComplete(); }
              @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc){
                ok[0] = ex.getMessage().contains("test");
                return super.onExceptionalCompletion(ex,cc);
              }
          });
          bh.asyncExec(nfs);
          // If the chosen file is too small for the cluster, some nodes will have *no* work
          // and so no exception is thrown.
          int MAX_CNT=5;
          while( !ok[0] && MAX_CNT-- > 0 ) {
            Thread.sleep(1000);
          }
        } catch( DException.DistributedException e ) {
          assertTrue(e.getMessage().contains("test"));
//        } catch( ExecutionException e ) { // caught on self
//          assertTrue(e.getMessage().contains("test"));
        } catch( java.lang.AssertionError ae ) {
          throw ae;             // Standard junit failure reporting assertion
        } catch(Throwable ex) {
          ex.printStackTrace();
          fail("Unexpected exception" + ex.toString());
        }
      }
    } finally {
      if( nfs != null ) nfs.remove(); // remove from DKV
    }
  }

  // Byte-wise histogram
  public static class ByteHistoThrow extends MRTask<ByteHistoThrow> {
    final H2ONode _throwAt;
    int[] _x;
    ByteHistoThrow( H2ONode h2o ) { _throwAt = h2o; }
    // Count occurrences of bytes
    @Override public void map( Chunk chk ) {
      _x = new int[256];            // One-time set histogram array
      byte[] bits = chk.getBytes(); // Raw file bytes
      for( byte b : bits )          // Compute local histogram
        _x[b&0xFF]++;
      if( H2O.SELF.equals(_throwAt) )
        throw new RuntimeException("test");
    }
    // ADD together all results
    @Override public void reduce( ByteHistoThrow bh ) { water.util.ArrayUtils.add(_x,bh._x); }
  }

  // Run tests when invoked from cmd line
  public static void main() throws InterruptedException, ExecutionException {
    MRThrow mrt = new MRThrow();

    H2O.waitForCloudSize(mrt._minCloudSize, 10000);
    mrt.testContinuationThrow();
  }
}
