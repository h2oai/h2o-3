package hex.psvm;

import hex.DataInfo;
import hex.FrameTask;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.psvm.psvm.IncompleteCholeskyFactorization;
import hex.psvm.psvm.Kernel;
import hex.psvm.psvm.PrimalDualIPM;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PSVM extends ModelBuilder<PSVMModel, PSVMModel.PSVMParameters, PSVMModel.PSVMModelOutput> {

  @Override
  public ModelCategory[] can_build() { return new ModelCategory[]{ModelCategory.Binomial}; }

  @Override
  public boolean isSupervised() { return true; }

  @Override
  public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }

  public PSVM(boolean startup_once) {
    super(new PSVMModel.PSVMParameters(), startup_once);
  }

  public PSVM(PSVMModel.PSVMParameters parms) {
    super(parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (expensive) {
      
    }
    // TODO: no validation yet - everything is allowed ;)
  }

  @Override
  public void checkDistributions() {
    if (_response.isCategorical()) {
      if (_response.cardinality() != 2) {
        error("_response", "Expected a binary categorical response, but instead got response with " + _response.cardinality() + " categories.");
      }
    } else {
      if (_response.min() != -1 || _response.max() != 1 || !_response.isInt() || _response.nzCnt() != _response.length()) {
        error("_response", "Non-categorical response provided, please make sure the response is either binary categorical response or uses only values -1/+1 in case of numerical response.");
      }
    }
  }

  @Override
  protected boolean computePriorClassDistribution() {
    return false; // no use, we don't output probabilities
  }

  @Override
  protected int init_getNClass() {
    return 2; // only binomial classification is supported
  }

  @Override
  protected Driver trainModelImpl() {
    return new SVMDriver();
  }

  private class SVMDriver extends Driver {

    DataInfo adaptTrain() {
      Frame adapted = new Frame(train());
      adapted.remove(_parms._response_column);
      if (response().naCnt() > 0) {
        throw new IllegalStateException("NA values in response column are currently not supported.");
      }
      Vec numericResp;
      if (response().domain() == null) {
        numericResp = response();
      } else {
        numericResp = new MRTask() {
          @Override
          public void map(Chunk c, NewChunk nc) {
            for (int i = 0; i < c._len; i++) {
              if (c.at8(i) == 0)
                nc.addNum(-1);
              else
                nc.addNum(+1);
            }
          }
        }.doAll(Vec.T_NUM, response()).outputFrame().vec(0);
      }
      adapted.add(_parms._response_column, numericResp);
      adapted.add("two_norm_sq", Scope.track(adapted.anyVec().makeZero())); // (L2 norm)^2; initialized 0 and actually calculated later
      // TODO: scaling / normalization
      return new DataInfo(adapted, null, 2, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false, null)
              .disableIntercept();
    }

    Frame prototypeFrame(DataInfo di) {
      Frame f = new Frame(di._adaptedFrame);
      f.remove("two_norm_sq");
      return f;
    }
    
    @Override
    public void computeImpl() {
      PSVMModel model = null;
      try {
        init(true);

        _job.update(0, "Initializing model training");
        final DataInfo di = adaptTrain();
        Scope.track_generic(di);
        DKV.put(di);

        if (_parms._gamma == -1) {
          _parms._gamma = 1.0d / di.fullN();
          Log.info("Set gamma = " + _parms._gamma);
        }
        
        final Vec response = di._adaptedFrame.vec(_parms._response_column);

        PSVMModel.PSVMModelOutput output = new PSVMModel.PSVMModelOutput(PSVM.this, prototypeFrame(di), response().domain());
        model = new PSVMModel(_result, _parms, output);
        model.delete_and_lock(_job);

        final int rank = getRankICF(_parms._rank_ratio, di._adaptedFrame.numRows());
        Log.info("Desired rank of ICF matrix = " + rank);

        _job.update(0, "Running Incomplete Cholesky Factorization");
        Frame icf = IncompleteCholeskyFactorization.icf(di, _parms.kernel(), rank, _parms._fact_threshold);
        Scope.track(icf);

        _job.update(0, "Running IPM");
        IPMInfo ipmInfo = new IPMInfo();
        Vec alpha = PrimalDualIPM.solve(icf, response, _parms.ipmParms(), ipmInfo);
        icf.remove();
        Log.info("IPM finished");

        Vec svs = new RegulateAlphaTask(_parms.c_pos(), _parms.c_neg(), _parms._sv_threshold)
                .doAll(Vec.T_NUM, alpha, response)
                .updateStats(model._output);
        assert svs.length() == model._output._svs_count;
        Scope.track(svs);

        // save alpha 
        Frame alphaFr = new Frame(Key.<Frame>make(model._key + "_alpha"));
        alphaFr.add("alpha", alpha);
        DKV.put(alphaFr);
        model._output._alpha_key = alphaFr._key;

        final int sampleSize = (int) Math.min(svs.length(), 1000);
        DataInfo.Row[] sampleSVs = new CollectSupportVecSamplesTask(di, svs, sampleSize)
                .doAll(di._adaptedFrame)
                .getSelected();

        model._output._rho = new CalculateRhoTask(di, sampleSVs, alpha, _parms.kernel())
                .doAll(di._adaptedFrame)
                .getRho();

        long estimatedSize = 0;
        for (DataInfo.Row sv : sampleSVs) {
          estimatedSize += toSupportVector(0, sv).estimateSize();
        }
        if (svs.length() > sampleSize) { // scale the estimate to the actual number of SVs
          estimatedSize = (estimatedSize * svs.length()) / sampleSize;
        }
        final boolean tooBig = estimatedSize >= Value.MAX;
        if (! tooBig) {
          Frame fr = new Frame(di._adaptedFrame);
          fr.add("__alpha", alpha);
          model._output._compressed_svs = new CompressVectorsTask(di).doAll(fr)._csvs;
          assert (svs.length() > sampleSize) || model._output._compressed_svs.length == estimatedSize; // sanity check - small models should have precise estimate
        } else {
          Log.err("Estimated model size (" + estimatedSize + "B) exceeds limits of DKV. Support vectors will not be stored.");
          model.addWarning("Model too big (size " + estimatedSize + "B) exceeds maximum model size. " +
                  "Support vectors will not be stored as a part of the model. You can still inspect what vectors were " +
                  "chosen and what are their alpha coefficients (see Frame alpha in model output).");
          model._output._compressed_svs = new byte[0];
        }

        Log.info("Total #support vectors: " + model._output._svs_count + " (size in memory " + estimatedSize + "B)");
        model._output._model_summary = createModelSummaryTable(model._output, ipmInfo);

        model.update(_job);

        if (! tooBig) {
          if (_parms._disable_training_metrics) {
            String noMetricsWarning = "Not creating training metrics: scoring disabled (use disable_training_metrics = false to override)";
            Log.warn(noMetricsWarning);
            model.addWarning(noMetricsWarning);
          } else {
            _job.update(0, "Scoring training frame");
            Frame scoringTrain = new Frame(train());
            model.adaptTestForTrain(scoringTrain, true, true);
            model._output._training_metrics = model.makeModelMetrics(train(), scoringTrain, "Training metrics");
          }
          if (valid() != null) {
            _job.update(0,"Scoring validation frame");
            Frame scoringValid = new Frame(valid());
            model.adaptTestForTrain(scoringValid, true, true);
            model._output._validation_metrics = model.makeModelMetrics(valid(), scoringValid, "Validation metrics");
          }
        }
        
        Scope.untrack(alpha._key); // we want to keep the alpha Vec after model is built
      } finally {
        if (model != null)
          model.unlock(_job);
      }
    }
  }

  private int getRankICF(double rankRatio, long numRows) {
    if (rankRatio == -1) {
      return (int) Math.sqrt(numRows);
    } else {
      return (int) (rankRatio * numRows);
    }
  }

  private static class CompressVectorsTask extends MRTask<CompressVectorsTask> {
    // IN
    private final DataInfo _dinfo;

    // OUT
    private byte[] _csvs; 

    CompressVectorsTask(DataInfo dinfo) {
      _dinfo = dinfo;
    }

    @Override
    public void map(Chunk[] acs) {
      AutoBuffer ab = new AutoBuffer();
      Chunk alpha = acs[acs.length - 1];
      Chunk[] cs = Arrays.copyOf(acs, acs.length - 1);
      DataInfo.Row row = _dinfo.newDenseRow();
      SupportVector sv = new SupportVector();
      for (int i = 0; i < alpha._len; i++) {
        if (alpha.isNA(i))
          continue;
        _dinfo.extractDenseRow(cs, i, row);
        sv.fill(alpha.atd(i), row.numVals, row.binIds);
        sv.compress(ab);
      }
      _csvs = ab.buf();
    }

    @Override
    public void reduce(CompressVectorsTask mrt) {
      _csvs = ArrayUtils.append(_csvs, mrt._csvs);
    }
  }

  private static SupportVector toSupportVector(double alpha, DataInfo.Row row) {
    if (row.isSparse()) {
      throw new UnsupportedOperationException("Sparse rows are not yet supported");
    }
    return new SupportVector().fill(alpha, row.numVals, row.binIds);
  }

  private static class CalculateRhoTask extends FrameTask<CalculateRhoTask> {

    // IN
    DataInfo.Row[] _selected;
    Vec _alpha;
    Kernel _kernel;

    // OUT
    double[] _rhos;

    // TEMP
    transient long _offset;
    transient Chunk _alphaChunk;

    public CalculateRhoTask(DataInfo dinfo, DataInfo.Row[] selected, Vec alpha, Kernel kernel) {
      super(null, dinfo);
      _selected = selected;
      _alpha = alpha;
      _kernel = kernel;
    }

    @Override
    public void map(Chunk[] chunks, NewChunk[] outputs) {
      _alphaChunk = _alpha.chunkForChunkIdx(chunks[0].cidx());
      _offset = _alphaChunk.start();
      _rhos = new double[_selected.length];
      super.map(chunks, outputs);
    }

    @Override
    protected boolean skipRow(long gid) {
      return _alphaChunk.isNA((int) (gid - _offset));
    }

    @Override
    protected void processRow(long gid, DataInfo.Row r) {
      for (int i = 0; i < _selected.length; i++) {
        _rhos[i] += _alphaChunk.atd((int) (gid - _offset)) * _kernel.calcKernel(r, _selected[i]);
      }
    }

    @Override
    public void reduce(CalculateRhoTask mrt) {
      _rhos = ArrayUtils.add(_rhos, mrt._rhos);
    }

    double getRho() {
      double rho = 0;
      for (int i = 0; i < _selected.length; i++) {
        rho += _selected[i].response[0] - _rhos[i];
      }
      return rho / _selected.length;
    }
  }

  private static class CollectSupportVecSamplesTask extends FrameTask<CollectSupportVecSamplesTask> {

    // IN
    private Vec _svs;
    private int _num_selected;

    // OUT
    DataInfo.Row[][] _selected;

    // TEMP
    private transient long[] _local_selected_idxs;

    @Override
    protected void setupLocal() {
      super.setupLocal();

      _selected = new DataInfo.Row[H2O.CLOUD.size()][];
      int[] cids = VecUtils.getLocalChunkIds(_svs);
      long local_svs = 0; 
      for (int cidx : cids) {
        local_svs += _svs.chunkLen(cidx);
      }
      final int local_contribution = (int) (_num_selected * local_svs / _svs.length());
      DataInfo.Row[] local_selected = new DataInfo.Row[local_contribution];
      _local_selected_idxs = new long[local_contribution];
      _selected[H2O.SELF.index()] = local_selected;
      
      int v = 0;
      fill_selected: for (int cidx : cids) {
        Chunk svIndices = _svs.chunkForChunkIdx(cidx);
        for (int i = 0; i < svIndices._len; i++) {
          _local_selected_idxs[v++] = svIndices.at8(i);
          if (v == local_contribution)
            break fill_selected;
        }
      }
      Arrays.sort(_local_selected_idxs);
    }

    CollectSupportVecSamplesTask(DataInfo dinfo, Vec svs, int num_selected) {
      super(null, dinfo);
      _svs = svs;
      _num_selected = num_selected;
    }

    @Override
    protected boolean skipRow(long gid) {
      return Arrays.binarySearch(_local_selected_idxs, gid) < 0;
    }

    @Override
    protected void processRow(long gid, DataInfo.Row r) {
      int idx = Arrays.binarySearch(_local_selected_idxs, gid);
      _selected[H2O.SELF.index()][idx] = r.deepClone();
    }

    @Override
    public void reduce(CollectSupportVecSamplesTask mrt) {
      for (int i = 0; i < H2O.CLOUD.size(); i++) {
        if (mrt._selected[i] != null)
          _selected[i] = mrt._selected[i];
      }
    }

    DataInfo.Row[] getSelected() {
      return ArrayUtils.flat(_selected);
    }
  }

  private static class RegulateAlphaTask extends MRTask<RegulateAlphaTask> {
    // IN
    private double _c_pos;
    private double _c_neg;
    private double _sv_threshold;

    // OUT
    long _svs_count; // support vectors
    long _bsv_count; // bounded support vectors

    private RegulateAlphaTask(double c_pos, double c_neg, double sv_threshold) {
      _c_pos = c_pos;
      _c_neg = c_neg;
      _sv_threshold = sv_threshold;
    }

    @Override
    public void map(Chunk alpha, Chunk label, NewChunk nc) {
      for (int i = 0; i < alpha._len; i++) {
        final double x = alpha.atd(i);
        if (x <= _sv_threshold) {
          alpha.setNA(i);
        } else {
          _svs_count++;
          nc.addNum(alpha.start() + i);
          double c = label.atd(i) > 0 ? _c_pos : _c_neg;
          double out_x;
          if (c - x <= _sv_threshold) {
            out_x = c;
            _bsv_count++;
          } else {
            out_x = x;
          }
          alpha.set(i, out_x * label.atd(i));
        }
      }
    }

    @Override
    public void reduce(RegulateAlphaTask mrt) {
      _svs_count += mrt._svs_count;
      _bsv_count += mrt._bsv_count;
    }

    private Vec updateStats(PSVMModel.PSVMModelOutput o) {
      o._svs_count = _svs_count;
      o._bsv_count = _bsv_count;
      return outputFrame().vec(0);
    }
  }

  private static TwoDimTable createModelSummaryTable(PSVMModel.PSVMModelOutput output, IPMInfo ipmInfo) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();

    colHeaders.add("Number of Support Vectors"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Number of Bounded Support Vectors"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Raw Model Size in Bytes"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("rho"); colTypes.add("double"); colFormat.add("%.5f");

    colHeaders.add("Number of Iterations"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Surrogate Gap"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Primal Residual"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Dual Residual"); colTypes.add("double"); colFormat.add("%.5f");

    final int rows = 1;
    TwoDimTable table = new TwoDimTable(
            "Model Summary", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    int col = 0;
    table.set(row, col++, output._svs_count);
    table.set(row, col++, output._bsv_count);
    table.set(row, col++, output._compressed_svs != null ? output._compressed_svs.length : -1);
    table.set(row, col++, output._rho);
    table.set(row, col++, ipmInfo._iter);
    table.set(row, col++, ipmInfo._sgap);
    table.set(row, col++, ipmInfo._resp);
    table.set(row, col++, ipmInfo._resd);
    assert col == colHeaders.size();

    return table;
  }

  private static class IPMInfo implements PrimalDualIPM.ProgressObserver {
    int _iter;
    double _sgap;
    double _resp;
    double _resd;
    boolean _converged;

    @Override
    public void reportProgress(int iter, double sgap, double resp, double resd, boolean converged) {
      _iter = iter;
      _sgap = sgap;
      _resp = resp;
      _resd = resd;
      _converged = converged;
    }
  }

}
