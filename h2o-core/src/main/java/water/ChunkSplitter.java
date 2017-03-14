package water;

import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.Log;

/** Helper to provide access to package
 * hidden methods and attributes.
 */
public class ChunkSplitter {
  /** Extract portion of given chunk into given output chunk. */
  public static void extractChunkPart(Chunk ic, Chunk oc, int startRow, int nrows, Futures fs) {
    try {
      NewChunk dst = new NewChunk(oc);
      dst._len = dst._sparseLen = 0;
      // Iterate over values skip all 0
      ic.extractRows(dst, startRow,startRow+nrows);
      // Handle case when last added value is followed by zeros till startRow+nrows
      assert dst._len == oc._len : "NewChunk.dst.len = " + dst._len + ", oc._len = " + oc._len;
      dst.close(dst.cidx(), fs);
    } catch(RuntimeException t){
      Log.err("got exception in chunkSplitter, ic = " + ic + ", oc = " + oc + " startRow = " + startRow + " nrows = " + nrows);
      throw t;
    }
  }
}
