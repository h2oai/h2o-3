package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMapLong;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SortTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBasic() {
    Frame fr = null;

    String tree = "(sort hex [0 1])";
    try {

      // Build a frame which is unsorted on small-count categoricals in columns
      // 0 and 1, and completely sorted on a record-number based column 2.
      // Sort will be on columns 0 and 1 and 2, in that order.
      fr = buildFrame(3,4);

      // 
      Val val = Rapids.exec(tree);
      System.out.println(val.toString());
      assertTrue( val instanceof ValFrame );
      Frame res = ((ValFrame)val)._fr;

      // assert that result is indeed sorted


    } finally {
      if( fr != null ) fr.delete();
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
