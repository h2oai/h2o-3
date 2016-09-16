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

  @Override protected Chunk makeChunk(int cidx){
    Chunk crows = rows().chunkForChunkIdx(cidx).getChunk(0);
    return new SubsetChunk(crows,_masterVec);
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

  // 
  static class SubsetChunk extends Chunk {
    final Chunk _crows;
    final VecAry.Reader _masterVec;
    protected SubsetChunk(Chunk crows,VecAry masterVec) {
      _masterVec = masterVec.reader(false);
      _crows  = crows;
    }
    @Override
    public double atd_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.at(rownum,0);
    }
    @Override
    public long   at8_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.at8(rownum,0);
    }
    @Override
    public boolean isNA_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.isNA(rownum,0);
    }

    @Override boolean set_impl(int idx, long l)   { return false; }
    @Override boolean set_impl(int idx, double d) { return false; }
    @Override boolean set_impl(int idx, float f)  { return false; }
    @Override boolean setNA_impl(int idx)         { return false; }

    @Override
    public int len() {return _crows.len();}

    @Override
    public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {throw H2O.unimpl();}

    @Override
    public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {throw H2O.unimpl();}

    @Override public boolean hasFloat() { return false; }
    public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw H2O.fail(); }
    @Override protected final void initFromBytes () { throw H2O.fail(); }
  }
}
