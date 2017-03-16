package water.fvec;

import org.junit.*;

import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

import water.TestUtil;
import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * Created by tomasnykodym on 3/28/14.
 */
public class SparseTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  private static void test_at(Chunk c, double [] vals, int [] nzs_ary){
    Random rnd = new Random(54321);
    // test atd
    for(int i =0 ; i < vals.length; ++i) {
      Assert.assertEquals(vals[i],c.atd(i),0);
    }

    for(int i =0 ; i < vals.length; ++i) {
      int j = rnd.nextInt(vals.length);
      Assert.assertEquals(vals[j],c.atd(j),0);
    }
    // test at8
    for(int i =0 ; i < vals.length; ++i) {
      if(Double.isNaN(vals[i])){
        Assert.assertTrue(c.isNA(i));
        try{
          c.at8(i);
          c.at8(i);
          Assert.assertFalse("should've thrown", true);
        } catch(RuntimeException rex){}
      } else {
        Assert.assertFalse(c.isNA(i));
        Assert.assertEquals((long) vals[i], c.at8(i), 0);
      }
    }
    // test random access
    for(int i =0 ; i < vals.length; ++i) {
      int j = rnd.nextInt(vals.length);
      Assert.assertEquals(vals[j],c.atd(j),0);
    }
  }

  private static void test_next_nz(Chunk c, int len, int [] nzs_ary){
    Random rnd = new Random(54321);
    // test next nz
    int x = -1;
    for(int i = 0; i < nzs_ary.length; ++i) {
      Assert.assertEquals(nzs_ary[i], c.nextNZ(x));
      x = nzs_ary[i];
    }
    Assert.assertEquals(len,c.nextNZ(x));
    for(int i = 0; i < nzs_ary.length; ++i) {
      int j = rnd.nextInt(len);
      int k = Arrays.binarySearch(nzs_ary,j);
      if(k < 0) k = -k-1; else k = k +1;
      Assert.assertEquals(nzs_ary[k], c.nextNZ(j));
    }
  }

  private static void test_get_doubles(Chunk c, double [] vals, int [] nzs_ary, boolean isSparse){
    double [] x = new double[vals.length];
    double NA = Double.MAX_VALUE;
    c.getDoubles(x,0,vals.length);
    Assert.assertArrayEquals(vals,x,0);
    Arrays.fill(x,0);
    c.getDoubles(x,0,vals.length,NA);
    for(int i =0 ; i < x.length; ++i)
      if(Double.isNaN(vals[i])){
          Assert.assertEquals(NA,x[i],0);
      } else Assert.assertEquals(vals[i],x[i],0);
    // test sparse doubles
    if(isSparse) {
      int[] ids = new int[x.length];
      int nzs = c.getSparseDoubles(x, ids);
      Assert.assertEquals(nzs_ary.length, nzs);
      Assert.assertArrayEquals(nzs_ary, Arrays.copyOf(ids, nzs));
      for (int i = 0; i < nzs; ++i) {
        Assert.assertEquals(vals[nzs_ary[i]], x[i], 0);
      }
    }
  }
  private static void test_extract_rows(Chunk c, double [] vals, int [] nzs_ary){
    NewChunk nc = new NewChunk(null, 0);
    c.extractRows(nc,0,vals.length);
    Assert.assertEquals(vals.length , nc.len());
    Chunk c2 = nc.compress();
    Assert.assertTrue(Arrays.equals(c.asBytes(), c2.asBytes()));

    nc = new NewChunk(null, 0);
    c.extractRows(nc,128,512);
    NewChunk nc2 = new NewChunk(null, 0);
    for(int i = 128; i < 512; i++)
      nc2.addNum(vals[i]);
    c2 = nc.compress();
    Chunk c3 = nc2.compress();
    Assert.assertTrue(Arrays.equals(c3.asBytes(), c2.asBytes()));

    int [] ids = new int[vals.length];
    int k = 0;
    int l = 0;
    for(int i = 0; i < ids.length; i += 8) {
      while(l < nzs_ary.length && nzs_ary[l] < i)
        ids[k++] = nzs_ary[l++];
      if(l < nzs_ary.length && nzs_ary[l] == i)
        ids[k++] = nzs_ary[l++];
      else
        ids[k++] = i;
    }
    ids = Arrays.copyOf(ids,k);

    nc = new NewChunk(null,0);
    nc2 = new NewChunk(null,0);
    c.extractRows(nc,ids);
    for(int i = 0; i < ids.length; i++){
      nc2.addNum(vals[ids[i]]);
    }
    c2 = nc.compress();
    c3 = nc2.compress();
    Assert.assertTrue(Arrays.equals(c2.asBytes(), c3.asBytes()));
  }



  public static Chunk makeAndTestSparseChunk(Class clz, double [] vals, int [] nzs_ary, boolean isNA, int off){
    NewChunk nc = new NewChunk(null,0);
    nc.addZeros(off);
    for(int i = 0; i < vals.length; ++i)
      nc.addNum(vals[i]);
    Chunk c = nc.compress();
    nzs_ary = nzs_ary.clone();
    for(int i =0; i < nzs_ary.length; ++i)
      nzs_ary[i] += off;
    Assert.assertTrue(clz.isInstance(c));
    if(isNA){
      Assert.assertTrue(c.isSparseNA());
      Assert.assertFalse(c.isSparseZero());
      Assert.assertEquals(nzs_ary.length,c.sparseLenNA());
      Assert.assertEquals(vals.length+off,c.sparseLenZero());
    } else {
      Assert.assertTrue(c.isSparseZero());
      Assert.assertFalse(c.isSparseNA());
      Assert.assertEquals(nzs_ary.length,c.sparseLenZero());
      Assert.assertEquals(vals.length+off,c.sparseLenNA());
    }
    // just test nzs
    test_next_nz(c,vals.length+off,nzs_ary);
    return c;
  }
  public static Chunk makeAndTestSparseChunk(Class clz, double [] vals, int [] nzs_ary, boolean isNA){
    return makeAndTestSparseChunk(clz,vals,nzs_ary,isNA,true);
  }
  public static Chunk makeAndTestSparseChunk(Class clz, double [] vals, int [] nzs_ary, boolean isNA, boolean isSparse){
    NewChunk nc = new NewChunk(null,0);
    for(int i = 0; i < vals.length; ++i)
      nc.addNum(vals[i]);
    Chunk c = nc.compress();
    Assert.assertTrue(clz.isInstance(c));
    if(isSparse) {
      if (isNA) {
        Assert.assertEquals(isSparse, c.isSparseNA());
        Assert.assertFalse(c.isSparseZero());
        Assert.assertEquals(nzs_ary.length, c.sparseLenNA());
        Assert.assertEquals(vals.length, c.sparseLenZero());
      } else {
        Assert.assertEquals(isSparse, c.isSparseZero());
        Assert.assertFalse(c.isSparseNA());
        Assert.assertEquals(nzs_ary.length, c.sparseLenZero());
        Assert.assertEquals(vals.length, c.sparseLenNA());
      }
    }
    test_at(c,vals,nzs_ary);
    if(isSparse)
      test_next_nz(c,vals.length,nzs_ary);
    test_extract_rows(c,vals,nzs_ary);

    test_get_doubles(c,vals,nzs_ary,isSparse);
    return c;
  }

  @Test
  public void testCXSChunk() {

    Random rnd = new Random(54321);
    for(boolean small:new boolean[]{false,true}) {
      for(boolean naSparse:new boolean[]{false,true}) {
        NewChunk nc = new NewChunk(null,0);
        TreeSet<Integer> nzsSet = new TreeSet<>();
        for (int i = 0; i < 96; ++i) {
          int x = rnd.nextInt(1024);
          while (!nzsSet.add(x)) x = rnd.nextInt(1024);
        }
        assert nzsSet.size() == 96:"size = " + nzsSet.size();
        int[] nzs_ary = new int[96];
        int k = 0;

        for (int x : nzsSet) nzs_ary[k++] = x;
        // small
        int prev = -1;
        double[] vals = new double[1024];
        if(naSparse) Arrays.fill(vals,Double.NaN);
        for (int x : nzs_ary) {
          if(naSparse)
            nc.addNAs(x-prev-1);
          else
            nc.addZeros(x - prev - 1);
          prev = x;
          long m = 1 + Short.MIN_VALUE + rnd.nextInt(Short.MAX_VALUE);
          int e = -3 + rnd.nextInt(small ? 1 : 3);
          nc.addNum(m, e);
          vals[x] = m * PrettyPrint.pow10(e);
        }
        if(naSparse)
          nc.addNAs(1024-prev-1);
        else
          nc.addZeros(1024 - prev - 1);
        Chunk c = nc.compress();
        NewChunk nc2 = new NewChunk(null,0);
        c.extractRows(nc2,0,1024);
        Assert.assertArrayEquals(c.asBytes(),nc2.compress().asBytes());
        System.out.println("compressed into " + c);
        Assert.assertTrue(c instanceof CXSChunk);
        CXSChunk cxs = (CXSChunk) c;
        if(small) Assert.assertEquals(4,cxs._elem_sz);
        else Assert.assertEquals(8,cxs._elem_sz);
        if(naSparse){
          Assert.assertTrue(cxs.isSparseNA());
          Assert.assertFalse(cxs.isSparseZero());
          Assert.assertEquals(nzs_ary.length,cxs.sparseLenNA());
          Assert.assertEquals(vals.length,cxs.sparseLenZero());
        } else {
          Assert.assertTrue(cxs.isSparseZero());
          Assert.assertFalse(cxs.isSparseNA());
          Assert.assertEquals(nzs_ary.length,cxs.sparseLenZero());
          Assert.assertEquals(vals.length,cxs.sparseLenNA());
        }
        double[] valsx = c.getDoubles(new double[1024], 0, 1024, Double.NaN);
        Assert.assertArrayEquals(vals, valsx, 1e-10);
        double [] sparse_vals = new double[1024];
        int [] sparse_ids = new int[1024];
        int nzs = c.getSparseDoubles(sparse_vals,sparse_ids);
        Assert.assertArrayEquals(nzs_ary,Arrays.copyOf(sparse_ids,nzs));
        for(int i = 0; i < nzs_ary.length; ++i){
          Assert.assertEquals(vals[nzs_ary[i]],sparse_vals[i],1e-10);
        }
        int x = -1;
        for(int i = 0; i < nzs; i++){
          x = c.nextNZ(x);
          Assert.assertEquals(nzs_ary[i],x);
        }
      }
    }
  }

  @Test
  public void doChunkTest() {
    double [] binary_vals = new double[1024];
    double [] valsZeroSmall;
    double [] valsZero;
    double [] valsNA;
    double [] float_vals;
    double [] double_vals;
    int [] nzs_ary;
    stall_till_cloudsize(1);
    valsZeroSmall = new double[1024];
    valsZero = new double[1024];
    valsNA = new double[1024];
    double [] valsNASmall = new double[1024];
    double [] valsBig = new double[1024];
    double [] valsNABig = new double[1024];
    float_vals = new double[1024];
    double_vals = new double[1024];
    double [] float_vals_na = new double[1024];
    double [] double_vals_na = new double[1024];
    Arrays.fill(float_vals_na,Double.NaN);
    Arrays.fill(double_vals_na,Double.NaN);
    Arrays.fill(valsNA,Double.NaN);
    Arrays.fill(valsNASmall,Double.NaN);
    Arrays.fill(valsNABig,Double.NaN);
    Random rnd = new Random(54321);
    TreeSet<Integer> nzs = new TreeSet<>();
    for(int i = 0; i < 96; i++) {
      int x = rnd.nextInt(valsZero.length);
      if(nzs.add(x)) {
        binary_vals[x] = 1;
        valsNA[x] =   rnd.nextDouble() < .95?rnd.nextInt():0;
        valsZero[x] = rnd.nextDouble() < .95?rnd.nextInt():Double.NaN;
        valsZeroSmall[x] = rnd.nextDouble() < .95?(rnd.nextInt(60000)-30000):Double.NaN;
        valsNASmall[x] = rnd.nextDouble() < .95?(rnd.nextInt(60000)-30000):0;
        valsBig[x] = rnd.nextDouble() < .95?((double)(long)(rnd.nextDouble()*Long.MAX_VALUE)):Double.NaN;
        valsNABig[x] = rnd.nextDouble() < .95?((double)(long)(rnd.nextDouble()*Long.MAX_VALUE)):0;
        float_vals[x] = rnd.nextDouble() < .95?rnd.nextFloat():Double.NaN;
        double_vals[x] = rnd.nextDouble() < .95?rnd.nextDouble():Double.NaN;
        float_vals_na[x] = rnd.nextDouble() < .95?rnd.nextFloat():0;
        double_vals_na[x] = rnd.nextDouble() < .95?rnd.nextDouble():0;
      }
    }
    nzs_ary = new int[nzs.size()];
    int k = 0;
    for(Integer i:nzs)
      nzs_ary[k++] = i;
    CXIChunk binaryChunk = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,binary_vals,nzs_ary,false);
    Assert.assertEquals(2,binaryChunk._elem_sz);
    CXIChunk binaryChunkLong = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,binary_vals,nzs_ary,false,1<<20);
    Assert.assertEquals(4,binaryChunkLong._elem_sz);
    SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsZero,nzs_ary,false);
    SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsNA,nzs_ary,true);
    CXIChunk smallZero = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsZeroSmall,nzs_ary,false);
    Assert.assertEquals(4,smallZero._elem_sz);
    CXIChunk smallZero2 = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsZeroSmall,nzs_ary,false,60000);
    Assert.assertEquals(4,smallZero2._elem_sz);
    CXIChunk smallZeroLong = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsZeroSmall,nzs_ary,false,1<<20);
    Assert.assertEquals(8,smallZeroLong._elem_sz);
    CXIChunk smallNA = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsNASmall,nzs_ary,true);
    Assert.assertEquals(4,smallNA._elem_sz);
    CXIChunk bigZero = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsBig,nzs_ary,false);
    Assert.assertEquals(12,bigZero._elem_sz);
    CXIChunk bigNAZero = (CXIChunk) SparseTest.makeAndTestSparseChunk(CXIChunk.class,valsNABig,nzs_ary,true);
    Assert.assertEquals(12,bigNAZero._elem_sz);
    CXFChunk floats = (CXFChunk) SparseTest.makeAndTestSparseChunk(CXFChunk.class,float_vals,nzs_ary,false);
    Assert.assertEquals(8,floats._elem_sz);
    CXFChunk doubles = (CXFChunk) SparseTest.makeAndTestSparseChunk(CXFChunk.class,double_vals,nzs_ary,false);
    Assert.assertEquals(12,doubles._elem_sz);
    CXFChunk floats_na = (CXFChunk) SparseTest.makeAndTestSparseChunk(CXFChunk.class,float_vals_na,nzs_ary,true);
    Assert.assertEquals(8,floats_na._elem_sz);
    CXFChunk doubles_na = (CXFChunk) SparseTest.makeAndTestSparseChunk(CXFChunk.class,double_vals_na,nzs_ary,true);
    Assert.assertEquals(12,doubles_na._elem_sz);
  }

}
