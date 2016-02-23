package water;

import java.util.Iterator;

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
      NewChunk src = new NewChunk(ic);
      src = ic.inflate_impl(src);
      assert src._len == ic._len;
      // Iterate over values skip all 0
      int remain = nrows;
      Iterator<NewChunk.Value> it = src.values(startRow, startRow + nrows);
      int off = startRow - 1;
      while (it.hasNext()) {
        NewChunk.Value v = it.next();
        final int rid = v.rowId0();
        assert rid < startRow + nrows;
        int add = rid - off; // number of values to add
        off = rid;
        if(src.isSparseNA()) dst.addNAs(add-1);
        else dst.addZeros(add - 1); // append (add-1) zeros
        v.add2Chunk(dst);    // followed by a value
        remain -= add;
        assert remain >= 0;
      }
      // Handle case when last added value is followed by zeros till startRow+nrows

      if(src.isSparseNA()) dst.addNAs(remain);
      else dst.addZeros(remain);

      assert dst._len == oc._len : "NewChunk.dst.len = " + dst._len + ", oc._len = " + oc._len;
      dst.close(dst.cidx(), fs);
    } catch(RuntimeException t){
      Log.err("gor exception in chunkSplitter, ic = " + ic + ", oc = " + oc + " startRow = " + startRow + " nrows = " + nrows);
      throw t;
    }
  }
}
