package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;
import water.rapids.vals.ValFrame;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SortTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBasicSortRapids() {
    Frame fr = null, res = null;

    // Stable sort columns 1 and 2
    String tree = "(sort hex [1 2])";
    try {

      // Build a frame which is unsorted on small-count categoricals in columns
      // 0 and 1, and completely sorted on a record-number based column 2.
      // Sort will be on columns 0 and 1, in that order, and is expected stable.
      fr = buildFrame(1000,10);
      fr.insertVec(0,"row",fr.remove(2));
      //
      Val val = Rapids.exec(tree);
      assertTrue( val instanceof ValFrame);
      res = val.getFrame();
      res.add("row",res.remove(0));
      new CheckSort().doAll(res);
    } finally {
      if( fr  != null ) fr .delete();
      if( res != null ) res.delete();
    }
  }

  @Test public void testBasicSortJava() {
    Frame fr = null, res = null;
    try {
      fr = buildFrame(1000,10);
      fr.insertVec(0,"row",fr.remove(2));
      res = Merge.sort(fr,new int[]{1,2});
      res.add("row",res.remove(0));
      new CheckSort().doAll(res);
    } finally {
      if( fr  != null ) fr .delete();
      if( res != null ) res.delete();
    }
  }

  @Test public void testBasicSortJava2() {
    Frame fr = null, res = null;
    try {
      fr = buildFrame(1000,10);
      String[] domain = new String[1000];
      for( int i=0; i<1000; i++ ) domain[i] = "D"+i;
      fr.vec(0).setDomain(domain);
      res = fr.sort(new int[]{0,1});
      new CheckSort().doAll(res);
    } finally {
      if( fr  != null ) fr .delete();
      if( res != null ) res.delete();
    }
  }


  // Assert that result is indeed sorted - on all 3 columns, as this is a
  // stable sort.
  private class CheckSort extends MRTask<CheckSort> {
    @Override public void map( Chunk cs[] ) {
      long x0 = cs[0].at8(0);
      long x1 = cs[1].at8(0);
      long x2 = cs[2].at8(0);
      for( int i=1; i<cs[0]._len; i++ ) {
        long y0 = cs[0].at8(i);
        long y1 = cs[1].at8(i);
        long y2 = cs[2].at8(i);
        assertTrue(x0<y0 || (x0==y0 && (x1<y1 || (x1==y1 && x2<y2))));
        x0=y0; x1=y1; x2=y2;
      }
      // Last row of chunk is sorted relative to 1st row of next chunk
      long row = cs[0].start()+cs[0]._len;
      if( row < cs[0].vec().length() ) {
        long y0 = cs[0].vec().at8(row);
        long y1 = cs[1].vec().at8(row);
        long y2 = cs[2].vec().at8(row);
        assertTrue(x0<y0 || (x0==y0 && (x1<y1 || (x1==y1 && x2<y2))));
      }
    }
  }

  // Build a 3 column frame.  Col #0 is categorical with # of cats given; col
  // #1 is categorical with 10x more choices.  A set of pairs of col#0 and
  // col#1 is made; each pair is given about 100 rows.  Col#2 is a row number.
  private static Frame buildFrame( int card0, int nChunks ) {
    // Compute the pairs
    int scale0 = 3;    // approximate ratio actual pairs vs all possible pairs; so scale0=3/scale1=10 is about 30% actual unique pairs
    int scale1 = 10;   // scale of |col#1| / |col#0|, i.e., col#1 has 10x more levels than col#0
    int scale2 = 100;  // number of rows per pair
    if( nChunks == -1 ) {
      long len = (long)card0*(long)scale0*(long)scale2;
      int rowsPerChunk = 100000;
      nChunks = (int)((len+rowsPerChunk-1)/rowsPerChunk);
    }
    NonBlockingHashMapLong<String> pairs_hash = new NonBlockingHashMapLong<>();
    Random R = new Random(card0*scale0*nChunks);
    for( int i=0; i<card0*scale0; i++ ) {
      long pair = (((long)R.nextInt(card0))<<32) | (R.nextInt(card0*scale1));
      if( pairs_hash.containsKey(pair) ) i--; // Reroll dice on collisions
      else pairs_hash.put(pair,"");
    }
    long[] pairs = pairs_hash.keySetLong();

    Key[] keys = new Vec.VectorGroup().addVecs(3);
    AppendableVec col0 = new AppendableVec(keys[0], Vec.T_NUM);
    AppendableVec col1 = new AppendableVec(keys[1], Vec.T_NUM);
    AppendableVec col2 = new AppendableVec(keys[2], Vec.T_NUM);

    NewChunk ncs0[] = new NewChunk[nChunks];
    NewChunk ncs1[] = new NewChunk[nChunks];
    NewChunk ncs2[] = new NewChunk[nChunks];

    for( int i=0; i<nChunks; i++ ) {
      ncs0[i] = new NewChunk(col0,i);
      ncs1[i] = new NewChunk(col1,i);
      ncs2[i] = new NewChunk(col2,i);
    }

    // inject random pairs into cols 0 and 1
    int len = pairs.length*scale2;
    for( int i=0; i<len; i++ ) {
      long pair = pairs[R.nextInt(pairs.length)];
      int nchk = R.nextInt(nChunks);
      ncs0[nchk].addNum( (int)(pair>>32),0);
      ncs1[nchk].addNum( (int)(pair    ),0);
    }

    // Compute data layout
    int espc[] = new int[nChunks+1];
    for( int i=0; i<nChunks; i++ )
      espc[i+1] = espc[i] + ncs0[i].len();

    // Compute row numbers into col 2
    for( int i=0; i<nChunks; i++ )
      for( int j=0; j<ncs0[i].len(); j++ )
        ncs2[i].addNum(espc[i]+j,0);

    Futures fs = new Futures();
    for( int i=0; i<nChunks; i++ ) {
      ncs0[i].close(i,fs);
      ncs1[i].close(i,fs);
      ncs2[i].close(i,fs);
    }

    Vec vec0 = col0.layout_and_close(fs);
    Vec vec1 = col1.layout_and_close(fs);
    Vec vec2 = col2.layout_and_close(fs);
    fs.blockForPending();
    Frame fr = new Frame(Key.<Frame>make("hex"), null, new Vec[]{vec0,vec1,vec2});
    DKV.put(fr);
    return fr;
  }

  @Test public void TestSortTimes() throws IOException {
    Frame fr=null, sorted=null;
    try {
      fr = parse_test_file("sort_crash.csv");
      sorted = fr.sort(new int[]{0});
      Vec vec = sorted.vec(0);
      int len = (int)vec.length();
      for( int i=1; i<len; i++ )
        assertTrue( vec.at8(i-1) <= vec.at8(i) );
    } finally {
      if( fr != null ) fr.delete();
      if( sorted != null ) sorted.delete();
    }
  }
}
