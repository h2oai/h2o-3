package hex.psvm.psvm;

import hex.DataInfo;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import water.junit.rules.ScopeTracker;
import water.TestFrameCatalog;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class KernelTest extends TestUtil {

  @Rule
  public ScopeTracker scope = new ScopeTracker();

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void testGaussianKernel() {
    DataInfo.Row[] rs = makeRows();
    Kernel gk = new GaussianKernel(0.01);

    for (DataInfo.Row r : rs) {
      assertEquals(1, gk.calcKernel(r, r), 0);
      assertEquals(1, gk.calcKernelWithLabel(r, r), 0);
    }

    assertEquals(0.924594, gk.calcKernel(rs[0], rs[1]), 1e-6);
    assertEquals(0.924594, gk.calcKernelWithLabel(rs[0], rs[1]), 1e-6);

    assertEquals(0.791678, gk.calcKernel(rs[0], rs[2]), 1e-6);
    assertEquals(-0.791678, gk.calcKernelWithLabel(rs[0], rs[2]), 1e-6);
  }

  private DataInfo.Row[] makeRows() { // few rows to test row operations (inner product, ...)
    Frame f = TestFrameCatalog.oneChunkFewRows();
    f.add("two_norm_sq", f.anyVec().makeZero());
    scope.track(f);

    DataInfo di = new DataInfo(f, null, 2, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false, null)
            .disableIntercept();

    Chunk[] chks = new Chunk[f.numCols()];
    for (int i = 0; i < chks.length; i++)
      chks[i] = di._adaptedFrame.vec(i).chunkForChunkIdx(0);

    DataInfo.Row[] rs = new DataInfo.Row[] {
            di.extractDenseRow(chks, 0, di.newDenseRow()),
            di.extractDenseRow(chks, 1, di.newDenseRow()),
            di.extractDenseRow(chks, 2, di.newDenseRow())
    };
    
    // inject L2^2
    for (DataInfo.Row r : rs) {
      r.response[1] = r.twoNormSq();
    }

    return rs;
  }

}
