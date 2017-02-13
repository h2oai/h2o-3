package water.fvec;

import water.*;

/**
 *  A simple wrapper for looking at only a subset of rows
 */
public class SubsetVec extends WrappedVec {
  final Key _subsetRowsKey;
  transient Vec _rows;          // Cached copy of the rows-Vec
  public SubsetVec(Key key, int rowLayout, VecAry masterVec, Key subsetRowsKey) {
    super(key, rowLayout, masterVec);
    _subsetRowsKey = subsetRowsKey;
  }
  public Vec rows() {
    if( _rows==null ) _rows = DKV.get(_subsetRowsKey).get();
    return _rows;
  }



  @Override public Futures remove_impl(Futures fs) {
    Keyed.remove(_subsetRowsKey,fs);
    return fs;
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) { 
    ab.putKey(_subsetRowsKey);
    return super.writeAll_impl(ab);
  }
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) { 
    ab.getKey(_subsetRowsKey,fs);
    return super.readAll_impl(ab,fs);
  }

  @Override
  public DBlock chunkIdx(int cidx) {
    Chunk crows = rows().chunkForChunkIdx(cidx).getChunk(0);
    return new SubsetChunk(crows,this,masterVec());
  }

  // 
  static class SubsetChunk extends Chunk {
    final Chunk _crows;
    final VecAry _masterVec;
    protected SubsetChunk(Chunk crows, SubsetVec vec, VecAry masterVec) {
      _masterVec = masterVec;
      _crows  = crows;
    }
    @Override public double atd(int idx) {
      long rownum = _crows.at8(idx);
      return _masterVec.at(rownum);
    }
    @Override public long at8(int idx) {
      long rownum = _crows.at8(idx);
      return _masterVec.at8(rownum);
    }
    @Override public boolean isNA(int idx) {
      long rownum = _crows.at8(idx);
      return _masterVec.isNA(rownum);
    }

    @Override protected boolean set_impl(int idx, long l)   { return false; }
    @Override protected boolean set_impl(int idx, double d) { return false; }
    @Override protected boolean set_impl(int idx, float f)  { return false; }
    @Override protected boolean setNA_impl(int idx)         { return false; }

    @Override
    public DVal getInflated(int i, DVal v) {
      throw H2O.unimpl();
    }

    @Override
    public int len() {
      return _crows.len();
    }

    @Override public boolean hasFloat() { return false; }

    @Override
    public Chunk deepCopy() {
      return this;
    }
    public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw H2O.fail(); }
  }
}
