package hex;

import water.*;
import water.H2O.H2OCountedCompleter;
import water.Job.JobCancelledException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

public abstract class FrameTask<T extends FrameTask<T>> extends MRTask<T>{
  protected transient DataInfo _dinfo;
  public DataInfo dinfo() { return _dinfo; }
  final Key _dinfoKey;
  final int [] _activeCols;
  final protected Key _jobKey;

  protected float _useFraction = 1.0f;
  private final long _seed;
  protected boolean _shuffle = false;

  public FrameTask(Key jobKey, DataInfo dinfo) {
    this(jobKey,dinfo._key,dinfo._activeCols,0xDECAFBEE);
  }
  public FrameTask(Key jobKey, DataInfo dinfo, long seed) {
    this(jobKey,dinfo._key,dinfo._activeCols,seed);
  }
  private FrameTask(Key jobKey, Key dinfoKey, int [] activeCols,long seed) {
    super(null);
    assert dinfoKey == null || DKV.get(dinfoKey) != null;
    _jobKey = jobKey;
    _dinfoKey = dinfoKey;
    _activeCols = activeCols;
    _seed = seed;
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
  protected void processRow(long gid, DataInfo.Row r){throw new RuntimeException("should've been overridden!");}
  protected void processRow(long gid, DataInfo.Row r, NewChunk [] outputs){throw new RuntimeException("should've been overridden!");}

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
  protected void chunkDone(double n){}


  /**
   * Extracts the values, applies regularization to numerics, adds appropriate offsets to categoricals,
   * and adapts response according to the CaseMode/CaseValue if set.
   */
  @Override public final void map(Chunk [] chunks, NewChunk [] outputs){
    if(_jobKey != null && !Job.isRunning(_jobKey))throw new JobCancelledException();
    final int nrows = chunks[0]._len;
    final long offset = chunks[0].start();
    chunkInit();

    Random skip_rng = null; //random generator for skipping rows

    //Example:
    // _useFraction = 0.8 -> 1 repeat with fraction = 0.8
    // _useFraction = 1.0 -> 1 repeat with fraction = 1.0
    // _useFraction = 1.1 -> 2 repeats with fraction = 0.55
    // _useFraction = 2.1 -> 3 repeats with fraction = 0.7
    // _useFraction = 3.0 -> 3 repeats with fraction = 1.0
    final int repeats = (int)Math.ceil(_useFraction);
    final float fraction = _useFraction / repeats;

    if (fraction < 1.0 || _dinfo._weights) {
      skip_rng = RandomUtils.getRNG(_seed+offset);
    }
    long[] shuf_map = null;
    if (_shuffle && ! _dinfo._weights) { //we are already shuffling when using row weights
      shuf_map = new long[nrows];
      for (int i=0;i<nrows;++i)
        shuf_map[i] = i;
      ArrayUtils.shuffleArray(shuf_map, new Random(_seed+offset).nextLong());
    }
    DataInfo.Row row = _dinfo.newDenseRow();
    float[] weight_map = null;
    float weight_sum;
    //TODO: store node-local helper arrays in _dinfo -> avoid re-allocation and construction
    if (_dinfo._weights) {
      weight_map = new float[nrows];
      weight_sum = 0;
      for (int i=0;i<nrows;++i) {
        row = _dinfo.extractDenseRow(chunks, i, row);
        weight_sum+=row.weight;
        weight_map[i]=weight_sum;
      }
      if (weight_sum > 0)
        ArrayUtils.div(weight_map, weight_sum); //normalize to 0...1
      else return; //nothing to do here - all rows have 0 weight
    }

    double num_processed_rows = 0;
    for(int rrr = 0; rrr < repeats; ++rrr) {
      OUTER:
      for(int rr = 0; rr < nrows; ++rr){
        // only train with a given number of training samples (fraction*nrows)
        if (skip_rng != null && skip_rng.nextFloat() > fraction)continue;

        // find out which training row to process
        int r;
        if (_dinfo._weights) {
          // importance sampling based on inverse of cumulative distribution
          float key = skip_rng.nextFloat();
          r = Arrays.binarySearch(weight_map, 0, nrows, key);
//          Log.info(Arrays.toString(weight_map));
//          Log.info("key: " + key + " idx: " + (r >= 0 ? r : (-r-1)));
          if (r<0) r=-r-1;
        } else {
          r = shuf_map != null ? (int) shuf_map[rr] : rr;
        }

        row = _dinfo.extractDenseRow(chunks, r, row);
        if(!row.bad) {
          long seed = offset + rrr * nrows + r;
          if (outputs != null && outputs.length > 0)
            processRow(seed++, row, outputs);
          else
            processRow(seed++, row);
        }
        assert(row.weight > 0); //check that we never process a row that was held out via row.weight = 0
        num_processed_rows += row.weight;
      }
    }
    chunkDone(num_processed_rows);
  }

}
