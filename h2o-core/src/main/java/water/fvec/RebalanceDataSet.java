package water.fvec;

import jsr166y.CountedCompleter;
import water.*;

import java.util.Arrays;

/**
 *  Created by tomasnykodym on 3/28/14.
 *
 *  Utility to rebalance dataset so that it has requested number of chunks and
 *  each chunk has the same number of rows +/-1.
 *
 *  It *does not* guarantee even chunk-node placement.  (This can not currently
 *  be done in H2O, since the placement of chunks is governed only by key-hash
 *  /vector group/ for Vecs)
 */
public class RebalanceDataSet extends H2O.H2OCountedCompleter {
  final VecAry _src;
  VecAry _dst;
  final int _nchunks;
  final Key _jobKey;
  final transient Vec.VectorGroup _vg;
  transient long[] _espc;

  /**
   * Constructor for make-compatible task.
   *
   * To be used to make frame compatible with other frame (i.e. make all vecs compatible with other vector group and rows-per-chunk).
   */
  public RebalanceDataSet(VecAry modelAry, VecAry srcAry) {
    this(modelAry,srcAry,null,null);
  }
  public RebalanceDataSet(VecAry modelAry, VecAry srcAry, H2O.H2OCountedCompleter cmp, Key jobKey) {
    super(cmp);
    _src = srcAry;
    _jobKey = jobKey;
    _espc = modelAry.espc(); // Get prior layout
    _vg = modelAry.group();
    _nchunks = modelAry.nChunks();
  }

  public RebalanceDataSet(VecAry src, int nchunks) { this(src, nchunks,null,null);}
  public RebalanceDataSet(VecAry src, int nchunks, H2O.H2OCountedCompleter cmp, Key jobKey) {
    super(cmp);
    _src = src;
    _nchunks = nchunks;
    _jobKey = jobKey;
    _vg = new Vec.VectorGroup();
  }

  public VecAry getResult(){join(); return _dst;}

  @Override public void compute2() {
    // Simply create a bogus new vector (don't even put it into KV) with
    // appropriate number of lines per chunk and then use it as a source to do
    // multiple makeZero calls to create empty vecs and than call RebalanceTask
    // on each one of them.  RebalanceTask will fetch the appropriate training_frame
    // chunks and fetch the data from them.
    long[] espc;
    if (_espc != null) espc = _espc;
    else {
      int rpc = (int) (_src.numRows() / _nchunks);
      int rem = (int) (_src.numRows() % _nchunks);
      espc = new long[_nchunks + 1];
      Arrays.fill(espc, rpc);
      for (int i = 0; i < rem; ++i) ++espc[i];
      long sum = 0;
      for (int i = 0; i < espc.length; ++i) {
        long s = espc[i];
        espc[i] = sum;
        sum += s;
      }
      assert espc[espc.length - 1] == _src.numRows() : "unexpected number of rows, expected " + _src.numRows() + ", got " + espc[espc.length - 1];
    }
    final int rowLayout = Vec.ESPC.rowLayout(_vg._key,espc);
    _dst = _vg.makeCons(rowLayout,_src.len(),0L);
    new RebalanceTask(this,_src).dfork(_dst);
  }


  @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller) {
    t.printStackTrace();
    if( _dst != null ) _dst.remove();
    return true;
  }

  public static class RebalanceTask extends MRTask<RebalanceTask> {
    final VecAry _src;
    public RebalanceTask(H2O.H2OCountedCompleter cmp, VecAry srcVecs){super(cmp);
      _src = srcVecs;}

    @Override public boolean logVerbose() { return false; }

    @Override public void map(Chunk [] chks){
      int n = chks[0]._len;
      long start = chks[0].start();
      for(int i = 0; i < chks.length; ++i)
        chks[i] = new NewChunk(chks[i]);
      int k = 0;
      while(k < n){
        Chunk [] srcChks = _src.getChunks(_src.elem2ChunkIdx(start + k),false).chks();
        long srcChunkStart = srcChks[0].start();
        int srcFrom = (int)(start+ k - srcChunkStart);
        final int srcTo = Math.min(srcChks[0]._len,srcFrom + n - k);
        for(int i = 0; i < srcChks.length; ++i)
          srcChks[i].add2NewChunk((NewChunk)chks[i],srcFrom,srcTo);
        k += srcTo - srcFrom;
      }
    }
  }
}
