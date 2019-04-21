package water.fvec;

import jsr166y.CountedCompleter;
import water.Futures;
import water.H2O;
import water.Key;
import water.MRTask;

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
  final Frame _in;
  final int _nchunks;
  Key _okey;
  Frame _out;
  final Key _jobKey;
  final transient Vec.VectorGroup _vg;
  transient long[] _espc;

  /**
   * Constructor for make-compatible task.
   *
   * To be used to make frame compatible with other frame (i.e. make all vecs compatible with other vector group and rows-per-chunk).
   */
  public RebalanceDataSet(Frame modelFrame, Frame srcFrame, Key dstKey) {this(modelFrame,srcFrame,dstKey,null,null);}
  public RebalanceDataSet(Frame modelFrame, Frame srcFrame, Key dstKey, H2O.H2OCountedCompleter cmp, Key jobKey) {
    super(cmp);
    _in = srcFrame;
    _jobKey = jobKey;
    _okey = dstKey;
    _espc = modelFrame.anyVec().espc(); // Get prior layout
    _vg = modelFrame.anyVec().group();
    _nchunks = modelFrame.anyVec().nChunks();
  }

  public RebalanceDataSet(Frame srcFrame, Key dstKey, int nchunks) { this(srcFrame, dstKey,nchunks,null,null);}
  public RebalanceDataSet(Frame srcFrame, Key dstKey, int nchunks, H2O.H2OCountedCompleter cmp, Key jobKey) {
    super(cmp);
    _in = srcFrame;
    _nchunks = nchunks;
    _jobKey = jobKey;
    _okey = dstKey;
    _vg = new Vec.VectorGroup();
  }

  public Frame getResult(){join(); return _out;}

  @Override public void compute2() {
    // Simply create a bogus new vector (don't even put it into KV) with
    // appropriate number of lines per chunk and then use it as a source to do
    // multiple makeZero calls to create empty vecs and than call RebalanceTask
    // on each one of them.  RebalanceTask will fetch the appropriate training_frame
    // chunks and fetch the data from them.
    long[] espc;
    if (_espc != null) espc = _espc;
    else {
      int rpc = (int) (_in.numRows() / _nchunks);
      int rem = (int) (_in.numRows() % _nchunks);
      espc = new long[_nchunks + 1];
      Arrays.fill(espc, rpc);
      for (int i = 0; i < rem; ++i) ++espc[i];
      long sum = 0;
      for (int i = 0; i < espc.length; ++i) {
        long s = espc[i];
        espc[i] = sum;
        sum += s;
      }
      assert espc[espc.length - 1] == _in.numRows() : "unexpected number of rows, expected " + _in.numRows() + ", got " + espc[espc.length - 1];
    }
    final int rowLayout = Vec.ESPC.rowLayout(_vg._key,espc);
    final Vec[] srcVecs = _in.vecs();
    _out = new Frame(_okey,_in.names(), new Vec(_vg.addVec(),rowLayout).makeCons(srcVecs.length,0L,_in.domains(),_in.types()));
    _out.delete_and_lock(_jobKey);
    new RebalanceTask(this,srcVecs).dfork(_out);
  }

  @Override public void onCompletion(CountedCompleter caller) {
    assert _out.numRows() == _in.numRows();
    Vec vec = _out.anyVec();
    assert vec.nChunks() == _nchunks;
    _out.update(_jobKey);
    _out.unlock(_jobKey);
  }
  @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller) {
    t.printStackTrace();
    if( _out != null ) _out.delete(_jobKey,new Futures()).blockForPending();
    return true;
  }

  public static class RebalanceTask extends MRTask<RebalanceTask> {
    final Vec [] _srcVecs;
    public RebalanceTask(H2O.H2OCountedCompleter cmp, Vec... srcVecs){super(cmp);_srcVecs = srcVecs;}

    @Override public boolean logVerbose() { return false; }

    private void rebalanceChunk(int i, Chunk c, NewChunk nc){
      final Vec srcVec = _srcVecs[i];
      final int N = c._len;
      int len = 0;
      int lastId = -1;
      while(N > len) {
        Chunk srcRaw = srcVec.chunkForRow(c._start+len);
        assert lastId == -1 ||
                lastId == srcRaw.cidx()-1 || // proceeded to the next chunk
                srcVec.chunk2StartElem(lastId+1) == srcVec.chunk2StartElem(srcRaw.cidx()); // skipped bunch of empty chunks
        lastId = srcRaw.cidx();
        int off = (int)((c._start+len) - srcRaw._start);
        assert off >=0 && off < srcRaw._len;
        int x = Math.min(N-len,srcRaw._len-off);
        srcRaw.extractRows(nc, off,off+x);
        len += x;
      }
      nc.close(_fs);
    }
    @Override public void map(Chunk [] chks){
      for(int c = 0; c < chks.length; ++c){
        rebalanceChunk(c,chks[c],new NewChunk(chks[c]));
      }
    }
  }
}
