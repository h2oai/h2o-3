package hex;

import water.*;
import water.H2O.H2OCountedCompleter;
import water.Job.JobCancelledException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.RandomUtils;

import java.util.Random;

public abstract class FrameTask<T extends FrameTask<T>> extends MRTask<T>{
  protected transient DataInfo _dinfo;
  public DataInfo dinfo() { return _dinfo; }
  final Key _dinfoKey;
  final int [] _activeCols;
  final protected Key _jobKey;
//  double    _ymu = Double.NaN; // mean of the response
  // size of the expanded vector of parameters

  protected float _useFraction = 1.0f;
  protected boolean _shuffle = false;

  public FrameTask(Key jobKey, DataInfo dinfo) {
    this(jobKey, dinfo._key, dinfo._activeCols,null);
  }
  public FrameTask(Key jobKey, DataInfo dinfo, H2OCountedCompleter cmp) {
    this(jobKey, dinfo._key, dinfo._activeCols,cmp);
  }
  public FrameTask(Key jobKey, Key dinfoKey, int [] activeCols) {
    this(jobKey,dinfoKey, activeCols,null);
  }
  public FrameTask(Key jobKey, Key dinfoKey, int [] activeCols, H2OCountedCompleter cmp) {
    super(cmp);
    assert dinfoKey == null || DKV.get(dinfoKey) != null;
    _jobKey = jobKey;
    _dinfoKey = dinfoKey;
    _activeCols = activeCols;
  }
  protected FrameTask(FrameTask ft){
    _dinfo = ft._dinfo;
    _jobKey = ft._jobKey;
    _useFraction = ft._useFraction;
    _shuffle = ft._shuffle;
    _activeCols = ft._activeCols;
    _dinfoKey = ft._dinfoKey;
    assert DKV.get(_dinfoKey) != null;
  }
  @Override protected void setupLocal(){
    DataInfo dinfo = DKV.get(_dinfoKey).get();
    _dinfo = _activeCols == null?dinfo:dinfo.filterExpandedColumns(_activeCols);
  }
  @Override protected void closeLocal(){ _dinfo = null;}

  /**
   * Method to process one row of the data for GLM functions.
   * Numeric and categorical values are passed separately, as is response.
   * Categoricals are passed as absolute indexes into the expanded beta vector, 0-levels are skipped
   * (so the number of passed categoricals will not be the same for every row).
   *
   * Categorical expansion/indexing:
   *   Categoricals are placed in the beginning of the beta vector.
   *   Each cat variable with n levels is expanded into n-1 independent binary variables.
   *   Indexes in cats[] will point to the appropriate coefficient in the beta vector, so e.g.
   *   assume we have 2 categorical columns both with values A,B,C, then the following rows will have following indexes:
   *      A,A - ncats = 0, we do not pass any categorical here
   *      A,B - ncats = 1, indexes = [2]
   *      B,B - ncats = 2, indexes = [0,2]
   *      and so on
   *
   * @param gid      - global id of this row, in [0,_adaptedFrame.numRows())
   */
  protected void processRow(long gid, DataInfo.Row r){throw new RuntimeException("should've been overriden!");}
  protected void processRow(long gid, DataInfo.Row r, NewChunk [] outputs){throw new RuntimeException("should've been overriden!");}

  @Override
  public T dfork(Frame fr){
    assert fr == _dinfo._adaptedFrame;
    return super.dfork(fr);
  }

  /**
   * Override this to initialize at the beginning of chunk processing.
   */
  protected void chunkInit(){}
  /**
   * Override this to do post-chunk processing work.
   * @param n Number of processed rows
   */
  protected void chunkDone(long n){}


  /**
   * Extracts the values, applies regularization to numerics, adds appropriate offsets to categoricals,
   * and adapts response according to the CaseMode/CaseValue if set.
   */
  @Override public final void map(Chunk [] chunks, NewChunk [] outputs){
    if(_jobKey != null && !Job.isRunning(_jobKey))throw new JobCancelledException();
    final int nrows = chunks[0]._len;
    final long offset = chunks[0].start();
    chunkInit();
    int start = 0;
    int end = nrows;

    Random skip_rng = null; //random generator for skipping rows

    //Example:
    // _useFraction = 0.8 -> 1 repeat with fraction = 0.8
    // _useFraction = 1.0 -> 1 repeat with fraction = 1.0
    // _useFraction = 1.1 -> 2 repeats with fraction = 0.55
    // _useFraction = 2.1 -> 3 repeats with fraction = 0.7
    // _useFraction = 3.0 -> 3 repeats with fraction = 1.0
    final int repeats = (int)Math.ceil(_useFraction);
    final float fraction = _useFraction / repeats;

    if (fraction < 1.0) {
      skip_rng = RandomUtils.getDeterRNG(new Random().nextLong());
    }
    long[] shuf_map = null;
    if (_shuffle) {
      shuf_map = new long[end-start];
      for (int i=0;i<shuf_map.length;++i)
        shuf_map[i] = start + i;
      ArrayUtils.shuffleArray(shuf_map, new Random().nextLong());
    }
    long num_processed_rows = 0;
    DataInfo.Row row = _dinfo.newDenseRow();
    for(int rrr = 0; rrr < repeats; ++rrr) {
      OUTER:
      for(int rr = start; rr < end; ++rr){
        final int r = shuf_map != null ? (int)shuf_map[rr-start] : rr;
        final long lr = r + chunks[0].start();
        if ((_dinfo._nfolds > 0 && (lr % _dinfo._nfolds) == _dinfo._foldId)
          || (skip_rng != null && skip_rng.nextFloat() > fraction))continue;
        ++num_processed_rows; //count rows with missing values even if they are skipped
        if(_dinfo.extractDenseRow(chunks, r, row).good) {
          long seed = offset + rrr * (end - start) + r;
          if (outputs != null && outputs.length > 0)
            processRow(seed, row, outputs);
          else
            processRow(seed, row);
        }
      }
    }
    chunkDone(num_processed_rows);
  }

}
