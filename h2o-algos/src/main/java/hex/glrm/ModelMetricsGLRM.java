package hex.glrm;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsGLRM extends ModelMetricsUnsupervised {
  public double _numerr;
  public double _caterr;

  public ModelMetricsGLRM(Model model, Frame frame, double numerr, double caterr) {
    super(model, frame, Double.NaN);
    _numerr = numerr;
    _caterr = caterr;
  }

  public static class GLRMModelMetrics extends MetricBuilderUnsupervised {
    public double _miscls;     // Number of misclassified categorical values
    public long _ncount;      // Number of observed numeric entries
    public long _ccount;     // Number of observed categorical entries
    public int[] _permutation;  // Permutation array for shuffling cols

    public GLRMModelMetrics(int dims, int[] permutation) {
      _work = new double[dims];
      _miscls = _ncount = _ccount = 0;
      _permutation = permutation;
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
        _ccount++;
      }

      int c = 0;
      for (int i = ncats; i < dataRow.length; i++) {
        int idx = _permutation[i];
        if (Double.isNaN(dataRow[idx])) { c++; continue; }
        double diff = (dataRow[idx] - sub[c]) * mul[c] - preds[idx];
        _sumsqe += diff * diff;
        _ncount++;
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
      _ncount += mm._ncount;
      _ccount += mm._ccount;
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      // double numerr = _ncount > 0 ? _sumsqe / _ncount : Double.NaN;
      // double caterr = _ccount > 0 ? _miscls / _ccount : Double.NaN;
      // return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, numerr, caterr));
      return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, _sumsqe, _miscls));
    }
  }
}