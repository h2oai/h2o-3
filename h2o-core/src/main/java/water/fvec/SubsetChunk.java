package water.fvec;

import water.*;

// A filtered ByteArraySupportedChunk; passed in the original data and a (chunk-relative) set of
// rows (also in ByteArraySupportedChunk for, for maximum compression).
public class SubsetChunk extends Chunk {
  final Chunk _data;          // All the data
  final Chunk _rows;          // The selected rows

  public SubsetChunk(Chunk data, Chunk rows, Vec subset_vec ) {
    _data = data; _rows = rows;
  }

  @Override
  public Chunk deepCopy() {return this;}

  @Override public double atd(int idx) { return _data.atd((int)_rows.at8(idx)); }
  @Override public long at8(int idx) { return _data.at8((int)_rows.at8(idx)); }

  // Returns true if the masterVec is missing, false otherwise
  @Override public boolean isNA(int idx) { return _data.isNA((int)_rows.at8(idx)); }
  @Override protected boolean set_impl(int idx, long l)   { return false; }
  @Override protected boolean set_impl(int idx, double d) { return false; }
  @Override protected boolean set_impl(int idx, float f)  { return false; }
  @Override protected boolean setNA_impl(int idx)         { return false; }

  @Override
  public DVal getInflated(int i, DVal v) {
    return _data.getInflated(_rows.at4(i),v);
  }

  @Override
  public int len() {return _rows.len();}

  public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw water.H2O.fail(); }
}
