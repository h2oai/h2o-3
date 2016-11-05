package water.udf;

import water.DKV;
import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Vec coming from a column
 */
public class VirtualVec<T> extends Vec {
  private final Column<T> column;

  public VirtualVec(Column<T> column) {
    super(VectorGroup.VG_LEN1.addVec(), column.rowLayout());
    this.column = column;
    DKV.put(_key, this);        // Header last
  }
  
  @Override public Chunk chunkForChunkIdx(int i) {
    final TypedChunk<T> typedChunk = column.chunkAt(i);
    return typedChunk.rawChunk();
  }
}
