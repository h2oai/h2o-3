package water.fvec;

/**
 * Created by tomas on 2/5/17.
 * Chunk which has only one non-zero integer value.
 * Created to reduce overhead of really sparse datasets.
 *
 */
public class C1XCChunk extends Chunk {
  final int _rowId;
  final int _val;

  public C1XCChunk(int rowId, int val){_rowId = rowId; _val = val;}
  @Override
  public Chunk deepCopy() {
    return new C1XCChunk(_rowId,_val);
  }

  @Override
  public double atd(int idx) {
    return 0;
  }

  @Override
  public long at8(int idx) {
    return 0;
  }

  @Override
  public boolean isNA(int idx) {
    return false;
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    return null;
  }

  @Override
  public int len() {return 1;}
}
