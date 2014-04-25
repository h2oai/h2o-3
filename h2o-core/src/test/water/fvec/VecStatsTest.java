package water.fvec;

import static org.junit.Assert.assertEquals;
import java.util.Random;
import org.junit.Test;
import water.TestUtil;
import water.Key;

public class VecStatsTest extends TestUtil {
  @Test public void test() {
    Frame frame = null;
    try {
    Random random = new Random();
    Vec[] vecs = new Vec[1];
    AppendableVec vec = new AppendableVec(Vec.newKey());
    for( int i = 0; i < 2; i++ ) {
      NewChunk chunk = new NewChunk(vec, i);
      for( int r = 0; r < 1000; r++ )
        chunk.addNum(random.nextInt(1000));
      chunk.close(i, null);
    }
    vecs[0] = vec.close(null);
    frame = new Frame(Key.make(), null, vecs);

    // Make sure we test the multi-chunk case
    vecs = frame.vecs();
    assert vecs[0].nChunks() > 1;
    long rows = frame.numRows();
    Vec v = vecs[0];
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, mean = 0, sigma = 0;
    for( int r = 0; r < rows; r++ ) {
      double d = v.at(r);
      if( d < min )
        min = d;
      if( d > max )
        max = d;
      mean += d;
    }
    mean /= rows;
    for( int r = 0; r < rows; r++ ) {
      double d = v.at(r);
      sigma += (d - mean) * (d - mean);
    }
    sigma = Math.sqrt(sigma / (rows - 1));

    double epsilon = 1e-9;
    assertEquals(max, v.max(), epsilon);
    assertEquals(min, v.min(), epsilon);
    assertEquals(mean, v.mean(), epsilon);
    assertEquals(sigma, v.sigma(), epsilon);
    } finally {
      if( frame != null ) frame.delete();
    }
  }
}