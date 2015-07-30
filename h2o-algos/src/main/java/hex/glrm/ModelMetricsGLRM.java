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

    public GLRMModelMetrics(int dims) {
      _work = new double[dims];
      _miscls = _ncount = _ccount = 0;
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      assert m instanceof GLRMModel;
      GLRMModel gm = (GLRMModel) m;
      assert gm._output._ncats + gm._output._nnums == dataRow.length;
      int ncats = gm._output._ncats;
      double[] sub = gm._output._normSub;
      double[] mul = gm._output._normMul;

      for (int i = 0; i < ncats; i++) {
        if (Double.isNaN(dataRow[i])) continue;
        if (dataRow[i] != preds[i]) _miscls++;
        _ccount++;
      }

      for (int i = ncats; i < dataRow.length; i++) {
        if (Double.isNaN(dataRow[i])) continue;
        int idx = i - ncats;
        double diff = (dataRow[i] - sub[idx]) * mul[idx] - preds[i];
        _sumsqe += diff * diff;
        _ncount++;
      }
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
      double numerr = _ncount > 0 ? _sumsqe / _ncount : Double.NaN;
      double caterr = _ccount > 0 ? _miscls / _ccount : Double.NaN;
      return m._output.addModelMetrics(new ModelMetricsGLRM(m, f, numerr, caterr));
    }
  }
}