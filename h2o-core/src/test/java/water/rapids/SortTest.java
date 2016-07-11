package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SortTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBasic() {
    Frame fr = null, res = null;

    // Stable sort columns 0 and 1
    String tree = "(sort hex [0 1])";
    try {

      // Build a frame which is unsorted on small-count categoricals in columns
      // 0 and 1, and completely sorted on a record-number based column 2.
      // Sort will be on columns 0 and 1, in that order, and is expected stable.
      fr = buildFrame(3,4);
      // 
      Val val = Rapids.exec(tree);
      System.out.println(val.toString());
      assertTrue( val instanceof ValFrame );
      res = ((ValFrame)val)._fr;

      // Assert that result is indeed sorted - on all 3 columns, as this is a
      // stable sort.
      final long max0 = (long)fr.vec(0).max();
      new MRTask() {
        @Override public void map( Chunk cs[] ) {
          long x0 = val(cs[0].at8(0),cs[1].at8(0),cs[2].at8(0));
          for( int i=1; i<cs[0]._len; i++ ) {
            long x1 = val(cs[0].at8(i),cs[1].at8(i),cs[2].at8(i));
            assertTrue(x0<x1);
            x0 = x1;
          }
          // Last row of chunk is sorted relative to 1st row of next chunk
          long row = cs[0].start()+cs[0]._len;
          if( row < cs[0].vec().length() ) {
            long x1 = val(cs[0].vec().at8(row),cs[1].vec().at8(row),cs[2].vec().at8(row));
            assertTrue(x0<x1);
          }
        }
        // Collapse 3 cols of data into a long
        private long val( long a, long b, long c ) { return ((b*max0+a)<<32)+c; }
      }.doAll(res);

    } finally {
      if( fr  != null ) fr .delete();
      if( res != null ) res.delete();
      Keyed.remove(Key.make("hex"));
    }
  }


  // Build a 3 column frame.  Col #0 is categorical with # of cats given; col
  // #1 is categorical with 10x more choices.  A set of pairs of col#0 and
  // col#1 is made; each pair is given about 100 rows.  Col#2 is a row number.
  private static Frame buildFrame( int card0, int nChunks ) {
    // Compute the pairs
    int scale0 = 3;
    int scale1 = 10;
    int scale2 = 100;
    NonBlockingHashMapLong<String> pairs_hash = new NonBlockingHashMapLong<>();
    Random R = new Random(card0*scale0*nChunks);
    for( int i=0; i<card0*scale0; i++ ) {
      long pair = (((long)R.nextInt(card0))<<32) | (R.nextInt(card0*scale1));
      if( pairs_hash.contains(pair) ) i--; // Reroll dice on collisions
      else pairs_hash.put(pair,"");
    }
    long[] pairs = pairs_hash.keySetLong();

    AppendableVec col0 = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    AppendableVec col1 = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    AppendableVec col2 = new AppendableVec(Vec.newKey(), Vec.T_NUM);

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
    Frame fr = new Frame(Key.make("hex"), null, new Vec[]{vec0,vec1,vec2});
    DKV.put(fr);
    return fr;
  }

}
