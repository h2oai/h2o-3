package water.fvec;

import water.*;

// A filtered Chunk; passed in the original data and a (chunk-relative) set of
// rows (also in Chunk for, for maximum compression).
public class SubsetChunk extends Chunk {
  final Chunk _data;          // All the data
  final Chunk _rows;          // The selected rows
  public SubsetChunk( Chunk data, Chunk rows, Vec subset_vec ) { 
    _data = data; _rows = rows; 
    set_len(rows._len);
    _start = rows._start; _vec = subset_vec; _cidx = rows._cidx;
    _mem = new byte[0];
  }
  
  @Override protected double atd_impl(int idx) { return _data.atd_impl((int)_rows.at8_impl(idx)); }
  @Override protected long   at8_impl(int idx) { return _data.at8_impl((int)_rows.at8_impl(idx)); }

  // Returns true if the masterVec is missing, false otherwise
  @Override protected boolean isNA_impl(int idx) { return _data.isNA_impl((int)_rows.at8_impl(idx)); }
  @Override boolean set_impl(int idx, long l)   { return false; }
  @Override boolean set_impl(int idx, double d) { return false; }
  @Override boolean set_impl(int idx, float f)  { return false; }
  @Override boolean setNA_impl(int idx)         { return false; }
  @Override public NewChunk inflate_impl(NewChunk  nc ) { throw water.H2O.fail(); }
  public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw water.H2O.fail(); }
  @Override protected final void initFromBytes () { throw water.H2O.fail(); }
}
