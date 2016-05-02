package ai.h2o.automl.guessers.column;

import ai.h2o.automl.colmeta.ColMeta;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMapLong;

/**
 * Check to see if the column is actually a uniq ID column (check uniques across Vec).
 */
public class IDGuesser extends Guesser {
  public IDGuesser(ColMeta cm) { super(cm); }

  @Override public void guess0(String name, Vec v) {
    if( _cm._response ) return; // dont care
    if( _cm._nameType==ColNameScanner.ID ) {
      assert v.isInt(): "ColNameScanner declared this column as ID, but column not integral!";

      // need to gather up some info on number of uniques in this column
      // if the number of uniques is ~nrow then want to ignore this column
      // for smaller datasets, it's ok to MR collect the grand set of uniques to a
      // single node... but as the number of rows grows (think 100M) then this becomes
      // prohibitive. As a rule of thumb, cap out number of possible uniques to 1M
      // after which we must try something else (heuristic based on unqies/chk)

      // Assume that ignorable ID columns are >= 0.
      // Using the pigeonhole principle, we can exclude any cases where the RANGE of
      // values is less than the the number of rows.
      long nrow;
      if( (v.min() >= 0) &&  (v.max() - v.min() >= (nrow=(v.length() - v.naCnt()))) ) {
        if( nrow > 5e6 )  // for > 5M rows, use a different strategy
          _cm._ignored = nrow-(new UniqTask().doAll(v))._size <= 10;
        else
          _cm._ignored = new UniqTaskPerChk().doAll(v)._fracUniq >= 0.75;
      }
    }
  }

  public static class UniqTask extends MRTask<UniqTask> {
    NonBlockingHashMapLong<String> _hm;
    int _size;
    @Override public void setupLocal() { _hm=new NonBlockingHashMapLong<>(); }
    @Override public void map(Chunk c) {
      for(int i=0;i<c._len;++i) _hm.putIfAbsent(c.at8(i),"");
    }
    @Override public void reduce(UniqTask t) {
      if( _hm!=t._hm ) _hm.putAll(t._hm);
      t._hm=null;
    }
    @Override public void postGlobal() { _size = _hm.size(); }
  }

  public static class UniqTaskPerChk extends MRTask<UniqTaskPerChk> {
    private double _fracUniq;
    @Override public void map(Chunk c) {
      NonBlockingHashMapLong<String> hm = new NonBlockingHashMapLong<>();
      for(int i=0;i<c._len;++i) hm.putIfAbsent(c.at8(i),"");
      _fracUniq = (double)hm.size()/(double)c._len;
    }
    @Override public void reduce(UniqTaskPerChk t) { _fracUniq+=t._fracUniq; }
    @Override public void postGlobal() { _fracUniq /= (double)_fr.anyVec().nChunks(); }
  }
}
