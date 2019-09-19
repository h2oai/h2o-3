package water.fvec;

import org.junit.Ignore;
import water.Value;

@Ignore // no actual test here: helps some tests to access package-private API of water.fvec.Vec
public class VecHelper {

  public static Value vecChunkIdx(Vec v, int cidx) {
    return v.chunkIdx(cidx);
  }

}
