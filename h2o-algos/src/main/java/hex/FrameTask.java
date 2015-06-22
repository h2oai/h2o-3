package hex;

import water.*;
import water.Job.JobCancelledException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
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
  private final int _iteration;

  public FrameTask(Key jobKey, DataInfo dinfo) {
    this(jobKey, dinfo, 0xDECAFBEE, -1);
  }
  public FrameTask(Key jobKey, DataInfo dinfo, long seed, int iteration) {
    this(jobKey,dinfo._key,dinfo._activeCols,seed,iteration);
  }
  private FrameTask(Key jobKey, Key dinfoKey, int [] activeCols,long seed, int iteration) {
    super(null);
    assert dinfoKey == null || DKV.get(dinfoKey) != null;
    _jobKey = jobKey;
    _dinfoKey = dinfoKey;
    _activeCols = activeCols;
    _seed = seed;
    _iteration = iteration;
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

  /**
   * Override this to initialize at the beginning of chunk processing.
   * @return whether or not to process this chunk
   */
  protected boolean chunkInit(){ return true; }
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
    boolean doWork = chunkInit();
    if (!doWork) return;
    final boolean obs_weights = _dinfo._weights && !_fr.vecs()[_dinfo.weightChunkId()].isConst();
    final double global_weight_sum = obs_weights ? _fr.vecs()[_dinfo.weightChunkId()].mean() * _fr.numRows() : 0;

    DataInfo.Row row = _dinfo.newDenseRow();
    double[] weight_map = null;
    double relative_chunk_weight = 1;
    //TODO: store node-local helper arrays in _dinfo -> avoid re-allocation and construction
    if (obs_weights) {
      weight_map = new double[nrows];
      double weight_sum = 0;
      for (int i=0;i<nrows;++i) {
        row = _dinfo.extractDenseRow(chunks, i, row);
        weight_sum+=row.weight;
        weight_map[i]=weight_sum;
        assert(i == 0 || row.weight == 0 || weight_map[i] > weight_map[i-1]);
      }
      if (weight_sum > 0) {
        ArrayUtils.div(weight_map, weight_sum); //normalize to 0...1
        relative_chunk_weight = global_weight_sum * nrows / _fr.numRows() / weight_sum;
      }
      else return; //nothing to do here - all rows have 0 weight
    }

    //Example:
    // _useFraction = 0.8 -> 1 repeat with fraction = 0.8
    // _useFraction = 1.0 -> 1 repeat with fraction = 1.0
    // _useFraction = 1.1 -> 2 repeats with fraction = 0.55
    // _useFraction = 2.1 -> 3 repeats with fraction = 0.7
    // _useFraction = 3.0 -> 3 repeats with fraction = 1.0
    final int repeats = (int)Math.ceil(_useFraction * relative_chunk_weight);
    final float fraction = (float)(_useFraction * relative_chunk_weight) / repeats;
    assert(fraction <= 1.0);

    final boolean sample = (fraction < 0.999 || obs_weights || _shuffle);
    final Random skip_rng = sample ? RandomUtils.getRNG((0x8734093502429734L+_seed+offset)*(_iteration+0x9823423497823423L)) : null;

    long num_processed_rows = 0;
    for(int rep = 0; rep < repeats; ++rep) {
      for(int row_idx = 0; row_idx < nrows; ++row_idx){
        int r = sample ? -1 : 0;
        // only train with a given number of training samples (fraction*nrows)
        if (sample && !obs_weights && skip_rng.nextDouble() > fraction) continue;
        if (obs_weights && num_processed_rows % 2 == 0) { //every second row is randomly sampled -> that way we won't "forget" rare rows
          // importance sampling based on inverse of cumulative distribution
          double key = skip_rng.nextDouble();
          r = Arrays.binarySearch(weight_map, 0, nrows, key);
//          Log.info(Arrays.toString(weight_map));
//          Log.info("key: " + key + " idx: " + (r >= 0 ? r : (-r-1)));
          if (r<0) r=-r-1;
          assert(r == 0 || weight_map[r] > weight_map[r-1]);
        } else if (r == -1){
          do {
            r = skip_rng.nextInt(nrows); //random sampling (with replacement)
          }
          // if we have weights, and we did the %2 skipping above, then we need to find an alternate row with non-zero weight
          while (obs_weights && ((r == 0 && weight_map[0] == 0) || (r > 0 && weight_map[r] == weight_map[r-1])));
        } else {
          assert(!obs_weights);
          r = row_idx; //linear scan - slightly faster
        }
        assert(r >= 0 && r<=nrows);

        row = _dinfo.extractDenseRow(chunks, r, row);
        if(!row.bad) {
          assert(row.weight > 0); //check that we never process a row that was held out via row.weight = 0
          long seed = offset + rep * nrows + r;
          if (outputs != null && outputs.length > 0)
            processRow(seed++, row, outputs);
          else
            processRow(seed++, row);
        }
        num_processed_rows++;
      }
    }
    assert(fraction != 1 || num_processed_rows == repeats * nrows);
    chunkDone(num_processed_rows);
  }

}
