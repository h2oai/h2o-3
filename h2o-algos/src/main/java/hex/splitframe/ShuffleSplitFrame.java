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

    // Do the split, into ratios.length groupings of NewChunks
    final int ncols = fr.numCols();
    MRTask mr = new MRTask() {
      @Override public void map( Chunk cs[], NewChunk ncs[] ) {
        Random rng = new Random(seed*cs[0].cidx());
        int nrows = cs[0]._len;
        for( int i=0; i<nrows; i++ ) {
          double r = rng.nextDouble();
          int x=0;              // Pick the NewChunk split
          for( ; x<ratios.length-1; x++ ) if( r<ratios[x] ) break;
          x *= ncols;
          // Helper string holder
          BufferedString vstr = new BufferedString();
          // Copy row to correct set of NewChunks
          for( int j=0; j<ncols; j++ ) {
            byte colType = cs[j].vec().get_type();
            switch (colType) {
              case Vec.T_BAD : break; /* NOP */
              case Vec.T_STR : ncs[x + j].addStr(cs[j], i); break;
              case Vec.T_UUID: ncs[x + j].addUUID(cs[j], i); break;
              case Vec.T_NUM : /* fallthrough */
              case Vec.T_CAT :
              case Vec.T_TIME:
                ncs[x + j].addNum(cs[j].atd(i));
                break;
              default:
                  throw new IllegalArgumentException("Unsupported vector type: " + colType);
            }
          }
        }
      }
    }.doAll(ncols*ratios.length,fr);

    // Build output frames
    Frame frames[] = new Frame[ratios.length];
    Vec[] vecs = fr.vecs();
    String[] names = fr.names();
    Futures fs = new Futures();
    for( int i=0; i<ratios.length; i++ ) {
      Vec[] nvecs = new Vec[ncols];
      for( int c=0; c<ncols; c++ ) {
        mr.appendables()[i*ncols+c].setDomain(vecs[c].domain());
        nvecs[c] = mr.appendables()[i*ncols+c].close(fs);
      }
      frames[i] = new Frame(keys[i],fr.names(),nvecs);
      DKV.put(frames[i],fs);
    }
    fs.blockForPending();
    return frames;
  }
}
