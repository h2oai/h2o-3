package hex.psvm;

import hex.*;
import hex.genmodel.algos.psvm.KernelParameters;
import hex.genmodel.algos.psvm.KernelType;
import hex.genmodel.algos.psvm.ScorerFactory;
import hex.genmodel.algos.psvm.SupportVectorScorer;
import hex.psvm.psvm.Kernel;
import hex.psvm.psvm.KernelFactory;
import hex.psvm.psvm.PrimalDualIPM;
import water.Futures;
import water.Key;
import water.Keyed;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;

public class PSVMModel extends Model<PSVMModel, PSVMModel.PSVMParameters, PSVMModel.PSVMModelOutput> {

  private transient SupportVectorScorer _scorer; // initialized lazily

  public PSVMModel(Key<PSVMModel> selfKey, PSVMParameters params, PSVMModelOutput output) {
    super(selfKey, params, output);
    assert(Arrays.equals(_key._kb, selfKey._kb));
  }

  ModelMetricsSupervised makeModelMetrics(Frame origFr, Frame adaptFr, String description) {
    Log.info("Making metrics: " + description);
    ModelMetrics.MetricBuilder mb = scoreMetrics(adaptFr);
    ModelMetricsSupervised mm = (ModelMetricsSupervised) mb.makeModelMetrics(this, origFr, adaptFr, null);
    mm._description = description;
    return mm;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    double svScore = getScorer().score0(data);
    return makePreds(svScore, preds);
  }

  private double[] makePreds(double svScore, double[] preds) {
    double pred = svScore + _output._rho;
    int label = pred < 0 ? 0 : 1;
    preds[0] = label;
    preds[1 + label] = 1;
    preds[2 - label] = 0;
    return preds;
  }
  
  @Override
  protected BigScorePredict setupBigScorePredict(BigScore bs) {
    BulkSupportVectorScorer bulkScorer = BulkScorerFactory.makeScorer(
            _parms._kernel_type, _parms.kernelParms(), _output._compressed_svs, (int) _output._svs_count, true);
    return new SVMBigScorePredict(bulkScorer);
  }

  private class SVMBigScorePredict implements BigScorePredict {
    private BulkSupportVectorScorer _bulkScorer;

    SVMBigScorePredict(BulkSupportVectorScorer bulkScorer) {
      _bulkScorer = bulkScorer;
    }

    @Override
    public BigScoreChunkPredict initMap(Frame fr, Chunk[] chks) {
      double[] scores = _bulkScorer.bulkScore0(chks);
      return new SVMBigScoreChunkPredict(scores);
    }
  }
  
  private class SVMBigScoreChunkPredict implements BigScoreChunkPredict {
    private final double[] _scores;

    private SVMBigScoreChunkPredict(double[] scores) {
      _scores = scores;
    }

    @Override
    public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
      return makePreds(_scores[row_in_chunk], preds);
    }

    @Override
    public void close() {
      // nothing to do
    } 
  }
  
  private SupportVectorScorer getScorer() {
    SupportVectorScorer svs = _scorer;
    if (svs == null) {
      _scorer = svs = ScorerFactory.makeScorer(_parms._kernel_type, _parms.kernelParms(), _output._compressed_svs);
    }
    return svs;
  }

  @SuppressWarnings("WeakerAccess")
  public static class PSVMParameters extends Model.Parameters {
    private static final PrimalDualIPM.Parms IPM_DEFAULTS = new PrimalDualIPM.Parms(); 

    public String algoName() { return "PSVM"; }
    public String fullName() { return "PSVM"; }
    public String javaName() { return PSVMModel.class.getName(); }

    @Override
    public long progressUnits() {
      return 1;
    }

    public long _seed = -1;

    // SVM
    public double _hyper_param = 1.0; // "C"
    public double _positive_weight = 1.0;
    public double _negative_weight = 1.0;
    public double _sv_threshold = 1.0e-4;
    public double _zero_threshold = 1.0e-9; // not exposed
    public boolean _disable_training_metrics = true;

    // Kernel
    public KernelType _kernel_type = KernelType.gaussian;
    public double _gamma = -1; // by default use 1/(#expanded features)

    // ** Expert **
    
    // ICF
    public double _rank_ratio = -1; // by default use sqrt(#rows)
    public double _fact_threshold = 1e-05;

    // Primal-Dual IPM
    public int _max_iterations = IPM_DEFAULTS._max_iter;
    public double _feasible_threshold = IPM_DEFAULTS._feasible_threshold;
    public double _surrogate_gap_threshold = IPM_DEFAULTS._feasible_threshold;
    public double _mu_factor = IPM_DEFAULTS._mu_factor;

    public Kernel kernel() {
      return KernelFactory.make(_kernel_type, kernelParms());
    }
    KernelParameters kernelParms() {
      KernelParameters kp = new KernelParameters();
      kp._gamma = _gamma;
      return kp;
    }

    PrimalDualIPM.Parms ipmParms() {
      PrimalDualIPM.Parms p = new PrimalDualIPM.Parms();
      p._max_iter = _max_iterations;
      p._mu_factor = _mu_factor;
      p._feasible_threshold = _feasible_threshold;
      p._sgap_threshold = _surrogate_gap_threshold;
      p._x_epsilon = _zero_threshold;
      p._c_pos = _hyper_param * _positive_weight;
      p._c_neg = _hyper_param * _negative_weight;
      return p;
    }

    double c_pos() {
      return _hyper_param * _positive_weight;
    }

    double c_neg() {
      return _hyper_param * _negative_weight;
    }
  }

  public static class PSVMModelOutput extends Model.Output {
    public long _svs_count; // support vectors
    public long _bsv_count; // bounded support vectors
    public double _rho;
    public Key<Frame> _alpha_key;
    public byte[] _compressed_svs; // might be empty if the model is too large (too many SVs)

    PSVMModelOutput(PSVM b, Frame f, String[] respDomain) { 
      super(b, f);
      _domains[_domains.length - 1] = respDomain != null ? respDomain : new String[]{"-1", "+1"};
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.Binomial;
    }
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new MetricBuilderPSVM(domain);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    Keyed.remove(_output._alpha_key, fs, true);
    return super.remove_impl(fs, cascade);
  }

}
