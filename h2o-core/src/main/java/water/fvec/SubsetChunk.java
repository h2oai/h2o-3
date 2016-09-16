package water.fvec;

import water.*;

// A filtered Chunk; passed in the original data and a (chunk-relative) set of
// rows (also in Chunk for, for maximum compression).
public class SubsetChunk extends Chunk {
  final Chunk _data;          // All the data
  final Chunk _rows;          // The selected rows
  public SubsetChunk( Chunk data, Chunk rows) {
    _data = data; _rows = rows;
    _mem = new byte[0];
  }
  public int len(){return _rows.len();}

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    throw H2O.unimpl();
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    throw H2O.unimpl();
  }

  @Override
  public double atd_impl(int idx) { return _data.atd_impl((int)_rows.at8_impl(idx)); }
  @Override
  public long   at8_impl(int idx) { return _data.at8_impl((int)_rows.at8_impl(idx)); }

  // Returns true if the masterVec is missing, false otherwise
  @Override
  public boolean isNA_impl(int idx) { return _data.isNA_impl((int)_rows.at8_impl(idx)); }
  @Override boolean set_impl(int idx, long l)   { return false; }
  @Override boolean set_impl(int idx, double d) { return false; }
  @Override boolean set_impl(int idx, float f)  { return false; }
  @Override boolean setNA_impl(int idx)         { return false; }

  public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw water.H2O.fail(); }
  @Override protected final void initFromBytes () { throw water.H2O.fail(); }
}
