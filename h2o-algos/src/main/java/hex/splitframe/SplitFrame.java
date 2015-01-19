package hex.splitframe;

import hex.Model;
import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.SplitFrameV2;
import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;

import static water.util.FrameUtils.generateNumKeys;

/**
 * Frame splitter function to divide given frame into
 * multiple partitions based on given ratios.
 *
 * <p>The task creates <code>ratios.length+1</code> output frame each containing a
 * demanded fraction of rows from source dataset</p>
 *
 * <p>The tasks internally extract data from source chunks and create output chunks in preserving order of parts.
 * I.e., the 1st partition contains the first P1-rows, the 2nd partition contains following P2-rows, ...
 * </p>
 *
 * <p>Assumptions and invariants</p>
 * <ul>
 * <li>number of demanding split parts is reasonable number, i.e., &lt;10. The task is not designed to split into many small parts.</li>
 * <li>the worker DOES NOT preserves distribution of new chunks over the cloud according to source dataset chunks.</li>
 * <li>rows inside one output chunk are not shuffled, they are extracted deterministically in the same order as they appear in source chunk.</li>
 * <li>workers can enforce data transfers if they need to obtain data from remote chunks.</li>
 * </ul>
 *
 * <p>NOTE: the implementation is data-transfer expensive and in some cases it would be beneficial to use original
 * implementation from <a href="https://github.com/0xdata/h2o/commits/9af3f4e">9af3f4e</a>.</p>.
 */
public class SplitFrame extends ModelBuilder<SplitFrameModel,SplitFrameModel.SplitFrameParameters,SplitFrameModel.SplitFrameOutput> {

  // Called from Nano thread; start the SplitFrame Job on a F/J thread
  public SplitFrame( SplitFrameModel.SplitFrameParameters parms ) { super("SplitFrame",parms); init(false); }

  public ModelBuilderSchema schema() { return new SplitFrameV2(); }

  @Override public SplitFrame trainModel() {
    return (SplitFrame)start(new SplitFrameDriver(), _parms.train().numCols()*_parms._ratios.length);
  }

  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{Model.ModelCategory.Unknown};
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the ratios.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    assert _parms._ratios.length > 0 : "No ratio specified!";
    assert _parms._ratios.length < 100 : "Too many frame splits demanded!";
    for( double p : _parms._ratios )
      if( p < 0.0 || p > 1.0 )
        error("ratios","Ratios must be between 0 and 1");
  }

  // ----------------------
  private class SplitFrameDriver extends H2OCountedCompleter<SplitFrameDriver> {

    /**
     * Temporary variable holding exceptions of workers
     */
    private Throwable[] workersExceptions;

    @Override
    protected void compute2() {
      // Lock all possible data
      Frame dataset = _parms.train();
      _parms.read_lock_frames(SplitFrame.this);
      init(true);
      _parms._destKeys = _parms._destKeys != null ? _parms._destKeys : generateNumKeys(dataset._key, _parms._ratios.length + 1);
      assert _parms._destKeys.length == _parms._ratios.length + 1 : "Unexpected number of destination keys.";

      final SplitFrameModel model = new SplitFrameModel(dest(), _parms, new SplitFrameModel.SplitFrameOutput(SplitFrame.this));
      // Create a template vector for each segment
      final Vec[][] templates = makeTemplates(dataset, _parms._ratios);
      final int nsplits = templates.length;
      assert nsplits == _parms._ratios.length + 1 : "Unexpected number of split templates!";
      // Launch number of distributed FJ for each split part
      final Vec[] datasetVecs = dataset.vecs();
      model._output._splits = new Frame[nsplits];
      for (int s = 0; s < nsplits; s++) {
        Frame split = new Frame(_parms._destKeys[s], dataset.names(), templates[s]);
        split.delete_and_lock(SplitFrame.this._key);
        model._output._splits[s] = split;
      }
      model.delete_and_lock(_key);
      setPendingCount(1);
      H2O.submitTask(new H2OCountedCompleter(SplitFrameDriver.this) {
        @Override
        public void compute2() {
          setPendingCount(nsplits);
          for (int s = 0; s < nsplits; s++) {
            new FrameSplitTask(new H2OCountedCompleter(this) { // Completer for this task
              @Override
              public void compute2() {
              }

              @Override
              public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
                synchronized (SplitFrameDriver.this) { // synchronized on this since can be accessed from different workers
                  workersExceptions = workersExceptions != null ? Arrays.copyOf(workersExceptions, workersExceptions.length + 1) : new Throwable[1];
                  workersExceptions[workersExceptions.length - 1] = ex;
                }
                tryComplete(); // we handle the exception so wait perform normal completion
                return false;
              }
            }, datasetVecs, _parms._ratios, s).asyncExec(model._output._splits[s]);
          }
          tryComplete(); // complete the computation of nsplits-tasks
        }
      });
      tryComplete(); // complete the computation of thrown tasks
      model.unlock(_key);
      _parms.read_unlock_frames(SplitFrame.this);
      boolean exceptional = workersExceptions != null;
      if (model._output._splits != null) {
        for (Frame s : model._output._splits) {
          if (s != null) {
            if (!exceptional) {
              s.update(_key);
              s.unlock(_key);
            } else {
              s.unlock(_key);
              s.delete(_key, new Futures()).blockForPending();
            }
          }
        }
      }
      done();
    }
  }

  // -------------------------------------------------------------------------

  // Make vector templates for all output frame vectors
  private Vec[][] makeTemplates(Frame dataset, double[] ratios) {
    Vec anyVec = dataset.anyVec();
    final long[][] espcPerSplit = computeEspcPerSplit(anyVec._espc, anyVec.length(), ratios);
    final int num = dataset.numCols(); // number of columns in input frame
    final int nsplits = espcPerSplit.length; // number of splits
    final String[][] domains = dataset.domains(); // domains
    final byte[] types = new byte[num];
    int j=0;
    for (Vec v : dataset.vecs()) types[j++] = v.get_type();

    Vec[][] t = new Vec[nsplits][/*num*/]; // resulting vectors for all
    for (int i=0; i<nsplits; i++) {
      // vectors for j-th split
      t[i] = new Vec(Vec.newKey(),espcPerSplit[i/*-th split*/]).makeCons(num, 0, domains, types);
    }
    return t;
  }

  // The task computes ESPC per split
  static long[/*nsplits*/][/*nchunks*/] computeEspcPerSplit(long[] espc, long len, double[] ratios) {
    assert espc.length>0 && espc[0] == 0;
    assert espc[espc.length-1] == len;
    long[] partSizes = partitione(len, ratios); // Split of whole vector
    int nparts = ratios.length+1;
    long[][] r = new long[nparts][espc.length]; // espc for each partition
    long nrows = 0;
    long start = 0;
    for (int p=0,c=0; p<nparts; p++) {
      int nc = 0; // number of chunks for this partition
      for(;c<espc.length-1 && (espc[c+1]-start) <= partSizes[p];c++) r[p][++nc] = espc[c+1]-start;
      if (r[p][nc] < partSizes[p]) r[p][++nc] = partSizes[p]; // last item in espc contains number of rows
      r[p] = Arrays.copyOf(r[p], nc+1);
      // Transfer rest of lines to the next part
      nrows = nrows-partSizes[p];
      start += partSizes[p];
    }
    return r;
  }


  /** MR task extract specified part of <code>_srcVecs</code>
   * into output chunk.*/
  private static class FrameSplitTask extends MRTask<FrameSplitTask> {
    final Vec  [] _srcVecs; // a source frame given by list of its columns
    final double[] _ratios;  // split ratios
    final int     _partIdx; // part index

    transient int _pcidx; // Start chunk index for this partition
    transient int _psrow; // Start row in chunk for this partition

    public FrameSplitTask(H2OCountedCompleter completer, Vec[] srcVecs, double[] ratios, int partIdx) {
      super(completer);
      _srcVecs = srcVecs;
      _ratios  = ratios;
      _partIdx = partIdx;
    }
    @Override protected void setupLocal() {
      // Precompute the first input chunk index and start row inside that chunk for this partition
      Vec anyInVec = _srcVecs[0];
      long[] partSizes = partitione(anyInVec.length(), _ratios);
      long pnrows = 0;
      for (int p=0; p<_partIdx; p++) pnrows += partSizes[p];
      long[] espc = anyInVec._espc;
      while (_pcidx < espc.length-1 && (pnrows -= (espc[_pcidx+1]-espc[_pcidx])) > 0 ) _pcidx++;
      assert pnrows <= 0;
      _psrow = (int) (pnrows + espc[_pcidx+1]-espc[_pcidx]);
    }
    @Override public void map(Chunk[] cs) { // Output chunks
      int coutidx = cs[0].cidx(); // Index of output Chunk
      int cinidx = _pcidx + coutidx;
      int startRow = coutidx > 0 ? 0 : _psrow; // where to start extracting
      int nrows = cs[0]._len;
      // For each output chunk extract appropriate rows for partIdx-th part
      for (int i=0; i<cs.length; i++) {
        // WARNING: this implementation does not preserve co-location of chunks so we are forcing here network transfer!
        ChunkSplitter.extractChunkPart(_srcVecs[i].chunkForChunkIdx(cinidx), cs[i], startRow, nrows, _fs);
      }
    }
  }
  static long[] partitione(long len, double[] ratio) {
    long[] r = new long[ratio.length+1];
    long sum = 0;
    int i = 0;
    float sr = 0;
    for (i=0; i<ratio.length; i++) {
      r[i] = (int) (ratio[i]*len);
      sum += r[i];
      sr  += ratio[i];
    }
    if (sr<1f) r[i] = len - sum;
    else r[i-1] += (len-sum);
    return r;
  }
}
