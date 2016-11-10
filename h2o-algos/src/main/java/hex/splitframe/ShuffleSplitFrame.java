package hex.splitframe;

import java.util.Random;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;

/** Frame splitter function to divide given frame into multiple partitions
 *  based on given ratios.
 *
 *  <p>The task creates <code>ratios.length+1</code> output frame each
 *  containing a demanded fraction of rows from source dataset</p>
 *
 *  Rows are selected at random for each split, but remain ordered.
 */
public class ShuffleSplitFrame {

  public static Frame[] shuffleSplitFrame( Frame fr, Key<Frame>[] keys, final double ratios[], final long seed ) {
    // Sanity check the ratios
    assert keys.length == ratios.length;
    double sum = ratios[0];
    for( int i = 1; i<ratios.length; i++ ) {
      sum += ratios[i];
      ratios[i] = sum;
    }
    assert water.util.MathUtils.equalsWithinOneSmallUlp(sum,1.0);
    byte[] types = fr.types();
    final int ncols = fr.numCols();
    byte[] alltypes = new byte[ncols*ratios.length];
    for( int i = 0; i<ratios.length; i++ )
      System.arraycopy(types,0,alltypes,i*ncols,ncols);

    // Do the split, into ratios.length groupings of NewChunks
    MRTask mr = new MRTask() {
      @Override public void map( ChunkAry cs, NewChunkAry ncs ) {
        DVal dv = new DVal();
        Random rng = new Random(seed*cs.cidx());
        int nrows = cs._len;
        for( int i=0; i<nrows; i++ ) {
          double r = rng.nextDouble();
          int x=0;              // Pick the NewChunk split
          for( ; x<ratios.length-1; x++ ) if( r<ratios[x] ) break;
          x *= ncols;
          // Copy row to correct set of NewChunks
          for( int j=0; j<ncols; j++ ) {
            ncs.addInflated(j,cs.getInflated(i,j,dv));
          }
        }
      }
    }.doAll(alltypes,fr);

    // Build output frames
    Frame frames[] = new Frame[ratios.length];
    Futures fs = new Futures();
    for( int i=0; i<ratios.length; i++ ) {
      Vec[] nvecs = new Vec[ncols];
      AppendableVec av = mr.appendables()[i];
      av.setDomains(fr.domains());
      frames[i] = new Frame(keys[i],fr.names(),av.layout_and_close(fs));
      DKV.put(frames[i],fs);
    }
    fs.blockForPending();
    return frames;
  }
}
