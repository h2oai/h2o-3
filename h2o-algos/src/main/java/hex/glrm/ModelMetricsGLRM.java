package hex.glrm;

import com.google.gson.JsonObject;
import hex.*;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.glrm.GlrmMojoModel;
import water.fvec.Frame;
import water.util.ComparisonUtils;

public class ModelMetricsGLRM extends ModelMetricsUnsupervised {
  public double _numerr;
  public double _caterr;
  public long   _numcnt;
  public long   _catcnt;

  public ModelMetricsGLRM(Model model, Frame frame, double numerr, double caterr, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
    _numerr = numerr;
    _caterr = caterr;
  }

  public ModelMetricsGLRM(Model model, Frame frame, double numerr, double caterr, long numcnt, long catcnt, CustomMetric customMetric) {
    this(model, frame, numerr, caterr, customMetric);
    _numcnt = numcnt;
    _catcnt = catcnt;
  }

  @Override
  public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
    super.isEqualUpToTolerance(comparator, other);
    ModelMetricsGLRM specificOther = (ModelMetricsGLRM) other;

    comparator.compareUpToTolerance("numerr", this._numerr, specificOther._numerr);
    comparator.compareUpToTolerance("caterr", this._caterr, specificOther._caterr);
    comparator.compare("numcnt", this._numcnt, specificOther._numcnt);
    comparator.compare("catcnt", this._catcnt, specificOther._catcnt);
    
    return comparator.isEqual();
  }

  public static class GlrmModelMetricsBuilder extends MetricBuilderUnsupervised<GlrmModelMetricsBuilder> {
    public double _miscls;     // Number of misclassified categorical values
    public long _numcnt;      // Number of observed numeric entries
    public long _catcnt;     // Number of observed categorical entries
    public int[] _permutation;  // Permutation array for shuffling cols
    public boolean _impute_original;

    public GlrmModelMetricsBuilder(int dims, int[] permutation) { this(dims, permutation, false); }
    public GlrmModelMetricsBuilder(int dims, int[] permutation, boolean impute_original) {
      _work = new double[dims];
      _miscls = _numcnt = _catcnt = 0;
      _permutation = permutation;
      _impute_original = impute_original;
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      assert m instanceof GLRMModel;
      GLRMModel gm = (GLRMModel) m;
      assert gm._output._ncats + gm._output._nnums == dataRow.length;
      int ncats = gm._output._ncats;
      double[] sub = gm._output._normSub;
      double[] mul = gm._output._normMul;

      // Permute cols so categorical before numeric since error metric different
      for (int i = 0; i < ncats; i++) {
        int idx = _permutation[i];
        if (Double.isNaN(dataRow[idx])) continue;
        if (dataRow[idx] != preds[idx]) _miscls++;
        _catcnt++;
      }

      int c = 0;
      for (int i = ncats; i < dataRow.length; i++) {
        int idx = _permutation[i];
        if (Double.isNaN(dataRow[idx])) { c++; continue; }
        double diff = (_impute_original ? dataRow[idx] : (dataRow[idx] - sub[c]) * mul[c]) - preds[idx];
        _sumsqe += diff * diff;
        _numcnt++;
        c++;
      }
      assert c == gm._output._nnums;
      return preds;
    }

    @Override
    public void reduce(GlrmModelMetricsBuilder mm) {
      super.reduce(mm);
      _miscls += mm._miscls;
      _numcnt += mm._numcnt;
      _catcnt += mm._catcnt;
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      // double numerr = _numcnt > 0 ? _sumsqe / _numcnt : Double.NaN;
      // double caterr = _catcnt > 0 ? _miscls / _catcnt : Double.NaN;
      // return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, numerr, caterr));
      return m.addModelMetrics(new ModelMetricsGLRM(m, f, _sumsqe, _miscls, _numcnt, _catcnt, _customMetric));
    }
  }

  public static class IndependentGLRMModelMetricsBuilder extends IndependentMetricBuilderUnsupervised<IndependentGLRMModelMetricsBuilder> {
    public double _miscls;     // Number of misclassified categorical values
    public long _numcnt;      // Number of observed numeric entries
    public long _catcnt;     // Number of observed categorical entries
    public int[] _permutation;  // Permutation array for shuffling cols
    public boolean _impute_original;
    private int _ncats;
    private int _nnums;
    private double[] _normSub;
    private double[] _normMul;

    public IndependentGLRMModelMetricsBuilder(int dims, int[] permutation, int ncats, int nnums, double[] normSub, double[] normMul) {
      this(dims, permutation, ncats, nnums, normSub, normMul, false); }
    public IndependentGLRMModelMetricsBuilder(int dims, int[] permutation, int ncats, int nnums, double[] normSub, double[] normMul, boolean impute_original) {
      _work = new double[dims];
      _miscls = _numcnt = _catcnt = 0;
      _permutation = permutation;
      _impute_original = impute_original;
      _ncats = ncats;
      _nnums = nnums;
      _normSub = normSub;
      _normMul = normMul;
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow) {
      assert _ncats + _nnums == dataRow.length;

      // Permute cols so categorical before numeric since error metric different
      for (int i = 0; i < _ncats; i++) {
        int idx = _permutation[i];
        if (Double.isNaN(dataRow[idx])) continue;
        if (dataRow[idx] != preds[idx]) _miscls++;
        _catcnt++;
      }

      int c = 0;
      for (int i = _ncats; i < dataRow.length; i++) {
        int idx = _permutation[i];
        if (Double.isNaN(dataRow[idx])) { c++; continue; }
        double diff = (_impute_original ? dataRow[idx] : (dataRow[idx] - _normSub[c]) * _normMul[c]) - preds[idx];
        _sumsqe += diff * diff;
        _numcnt++;
        c++;
      }
      assert c == _nnums;
      return preds;
    }

    @Override
    public void reduce(IndependentGLRMModelMetricsBuilder mm) {
      super.reduce(mm);
      _miscls += mm._miscls;
      _numcnt += mm._numcnt;
      _catcnt += mm._catcnt;
    }

    @Override
    public ModelMetrics makeModelMetrics() {
      // double numerr = _numcnt > 0 ? _sumsqe / _numcnt : Double.NaN;
      // double caterr = _catcnt > 0 ? _miscls / _catcnt : Double.NaN;
      // return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, numerr, caterr));
      return new ModelMetricsGLRM(null, null, _sumsqe, _miscls, _numcnt, _catcnt, _customMetric);
    }
  }

  public static class GLRMMetricBuilderFactory extends ModelMetrics.MetricBuilderFactory<GLRMModel, GlrmMojoModel> {
    @Override
    public IMetricBuilder createBuilder(GlrmMojoModel mojoModel, JsonObject extraInfo) {
      int k = mojoModel._ncolX;
      int[] permutation = mojoModel._permutation;
      Object imputeOriginalObject = mojoModel._modelAttributes.getParameterValueByName("impute_original");
      Boolean imputeOriginal = false;
      if (imputeOriginalObject != null) {
        imputeOriginal = (Boolean)imputeOriginalObject;
      }
      int ncats = mojoModel._ncats;
      int nnums = mojoModel._nnums;
      double[] normSub = mojoModel._normSub;
      double[] normMul = mojoModel._normMul;
      return new IndependentGLRMModelMetricsBuilder(k, permutation, ncats, nnums, normSub, normMul, imputeOriginal);
    }
  }
}
