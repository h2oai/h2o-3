package water.fvec;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.Arrays;
import java.util.Iterator;

public class CNAXDChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);
    int K = 100;
    double[] vals = new double[K];
    for (int i=0;i<K-1;i++) vals[i] = Double.NaN;
    for(double v: vals) nc.addNum(v);
    double extra = 1.2;
    nc.addNum(extra);
    //nc: {(K-1)* NaN, 0, 1.2}  
    
    Chunk cc = nc.compress();
    Assert.assertEquals(K + 1, cc._len);
    Assert.assertTrue(cc instanceof CNAXDChunk);
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], cc.atd(i), 0);
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], cc.at_abs(i), 0);
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(cc.isNA(i));
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(cc.isNA_abs(i));
    Assert.assertEquals(extra, cc.atd(K), 0);
    Assert.assertEquals(extra, cc.at_abs(K), 0);
    Assert.assertFalse(cc.isNA(K));
    Assert.assertFalse(cc.isNA_abs(K));
    double[] sparsevals = new double[cc.sparseLenNA()];
    int[] sparseids = new int[cc.sparseLenNA()];
    int N=cc.asSparseDoubles(sparsevals, sparseids);
    assert(N==sparsevals.length);
    for (int i = 0; i < sparsevals.length; ++i) {
      if (cc.isNA(sparseids[i])) Assert.assertTrue(Double.isNaN(sparsevals[i]));
      else Assert.assertTrue(cc.atd(sparseids[i])==sparsevals[i]);
    }
    double[] densevals = new double[cc.len()];
    cc.getDoubles(densevals,0,cc.len());
    for (int i = 0; i < densevals.length; ++i) {
      if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
      else Assert.assertTrue(cc.atd(i)==densevals[i]);
    }
    
    nc = new NewChunk(null, 0);
    cc.inflate_impl(nc);
    nc.values(0, nc._len);
    Assert.assertEquals(K+1, nc._len);
    Assert.assertEquals(2, nc._sparseLen);
    Iterator<NewChunk.Value> it = nc.values(0, K+1);
    Assert.assertTrue(it.next().rowId0() == K-1);
    Assert.assertTrue(it.next().rowId0() == K);
    Assert.assertFalse(it.hasNext());
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], nc.atd(i), 0);
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], nc.at_abs(i), 0);
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(nc.isNA(i));
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(nc.isNA_abs(i));
    
    Chunk cc2 = nc.compress();
    Assert.assertEquals(K + 1, cc2._len);
    Assert.assertTrue(cc2 instanceof CNAXDChunk);
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], cc2.atd(i), 0);
    for (int i = 0; i < K; ++i) Assert.assertEquals(vals[i], cc2.at_abs(i), 0);
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(cc2.isNA(i));
    for (int i = 0; i < K-1; ++i) Assert.assertTrue(cc2.isNA_abs(i));
    Assert.assertEquals(extra, cc2.atd(K), 0);
    Assert.assertEquals(extra, cc2.at_abs(K), 0);
    Assert.assertFalse(cc2.isNA(K));
    Assert.assertFalse(cc2.isNA_abs(K));
    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
