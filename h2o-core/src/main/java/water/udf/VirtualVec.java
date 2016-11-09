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
    DKV.put(_key, this);
  }
  
  @Override public Chunk chunkForChunkIdx(int i) {
    final TypedChunk<T> typedChunk = column.chunkAt(i);
    return typedChunk.rawChunk();
  }

  @Override
  public final byte [] asBytes(){return new byte[0];}
  
  @Override public String toString() {
    return "VirtualVec(" + column + ")";
  }
}
