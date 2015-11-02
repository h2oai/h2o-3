package hex.glrm;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsGLRM extends ModelMetricsUnsupervised {
  public double _numerr;
  public double _caterr;
  public long   _numcnt;
  public long   _catcnt;

  public ModelMetricsGLRM(Model model, Frame frame, double numerr, double caterr) {
    super(model, frame, Double.NaN);
    _numerr = numerr;
    _caterr = caterr;
  }

  public ModelMetricsGLRM(Model model, Frame frame, double numerr, double caterr, long numcnt, long catcnt) {
    this(model, frame, numerr, caterr);
    _numcnt = numcnt;
    _catcnt = catcnt;
  }

  public static class GLRMModelMetrics extends MetricBuilderUnsupervised {
    public double _miscls;     // Number of misclassified categorical values
    public long _numcnt;      // Number of observed numeric entries
    public long _catcnt;     // Number of observed categorical entries
    public int[] _permutation;  // Permutation array for shuffling cols
    public boolean _impute_original;

    public GLRMModelMetrics(int dims, int[] permutation) { this(dims, permutation, false); }
    public GLRMModelMetrics(int dims, int[] permutation, boolean impute_original) {
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
    public void reduce(MetricBuilder mb) {
      GLRMModelMetrics mm = (GLRMModelMetrics) mb;
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
      return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, _sumsqe, _miscls, _numcnt, _catcnt));
    }
  }
}