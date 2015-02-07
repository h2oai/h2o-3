package hex.kmeans;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ModelMetricsKMeans extends ModelMetricsUnsupervised {
  public long[/*k*/] _size;
  public double[/*k*/] _within_mse; //TODO: add Clustering specific members
  public double _avg_ss;
  public double _avg_within_ss;
  public double _avg_between_ss;

  public ModelMetricsKMeans(Model model, Frame frame) {
    super(model, frame);
    _size = null;
    _within_mse = null;
    _avg_ss = _avg_within_ss = _avg_between_ss = Double.NaN;
  }

  public static class MetricBuilderKMeans extends ModelMetricsUnsupervised.MetricBuilderUnsupervised {
    public long[] _size;        // Number of elements in cluster
    public double[] _within_sumsqe;   // Within-cluster sum of squared error

    public MetricBuilderKMeans(int dims) {
      _size = new long[dims];
      _within_sumsqe = new double[dims];
      Arrays.fill(_size, 0);
      Arrays.fill(_within_sumsqe, 0);
      _work = new float[dims];
    }

    // Compare row (dataRow) against centroid it was assigned to (preds[0])
    @Override
    public float[] perRow(float[] preds, float[] dataRow, Model m) {
      assert m instanceof KMeansModel;
      if (Float.isNaN(preds[0])) return dataRow; // No errors if   actual   is missing
      if (Float.isNaN(dataRow[0])) return dataRow; // No errors if prediction is missing

      final TwoDimTable centers = ((KMeansModel) m)._output._centers;
      assert (dataRow.length == centers.getColDim());
      final int clus = (int) preds[0];   // Assigned cluster index
      assert 0 <= clus && clus < _within_sumsqe.length;

      // Compute error
      for (int i = 0; i < dataRow.length; ++i) {
        double err = (double) centers.get(clus, i) - dataRow[i]; // Error: distance from assigned cluster center
        _sumsqe += err * err;       // Squared error
        _within_sumsqe[clus] += err * err;
      }
      assert !Double.isNaN(_sumsqe);
      _size[clus]++;
      _count++;
      return dataRow;                // Flow coding
    }

    @Override
    public void reduce(MetricBuilder mb) {
      MetricBuilderKMeans mm = (MetricBuilderKMeans) mb;
      super.reduce(mm);
      ArrayUtils.add(_size, mm._size);
      ArrayUtils.add(_within_sumsqe, mm._within_sumsqe);
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) {
      assert m instanceof KMeansModel;
      KMeansModel km = (KMeansModel) m;
      ModelMetricsKMeans mm = new ModelMetricsKMeans(m, f);

      mm._size = _size;
      mm._avg_within_ss = _sumsqe / _count;
      mm._within_mse = new double[_size.length];
      for (int i = 0; i < mm._within_mse.length; i++)
        mm._within_mse[i] = _within_sumsqe[i] / _size[i];

      Vec[] vecs = f.vecs();
      final double[] means = prepMeans(vecs);
      final double[] mults = prepMults(vecs, km._parms._standardize);

      // Sum-of-square distance from grand mean
      if (km._parms._k == 1)
        mm._avg_ss = mm._avg_within_ss;
      else {
        // If data already standardized, grand mean is just the origin
        TotSS totss = new TotSS(means, mults).doAll(vecs);
        mm._avg_ss = totss._tss / f.numRows(); // mse with respect to grand mean
      }
      mm._avg_between_ss = mm._avg_ss - mm._avg_within_ss;
      return m.addMetrics(mm);
    }

    // means are used to impute NAs
    double[] prepMeans(final Vec[] vecs) {
      final double[] means = new double[vecs.length];
      for (int i = 0; i < vecs.length; i++) means[i] = vecs[i].mean();
      return means;
    }

    // mults & means for standardization
    double[] prepMults(final Vec[] vecs, final boolean standardize) {
      if (!standardize) return null;
      double[] mults = new double[vecs.length];
      for (int i = 0; i < vecs.length; i++) {
        double sigma = vecs[i].sigma();
        mults[i] = standardize(sigma) ? 1.0 / sigma : 1.0;
      }
      return mults;
    }

    private static boolean standardize(double sigma) {
      // TODO unify handling of constant columns
      return sigma > 1e-6;
    }
  }

  // Initial sum-of-square-distance to nearest cluster center
  private static class TotSS extends MRTask<TotSS> {
    // IN
    double[] _means, _mults;

    // OUT
    double _tss;

    TotSS(double[] means, double[] mults) {
      _means = means;
      _mults = mults;
      _tss = 0;
    }

    @Override
    public void map(Chunk[] cs) {
      for (int row = 0; row < cs[0]._len; row++) {
        for (int i = 0; i < cs.length; i++) {
          double d = cs[i].atd(row);
          if (Double.isNaN(d)) continue;
          d = (d - _means[i]) * (_mults == null ? 1 : _mults[i]);
          _tss += d * d;
        }
      }
      _means = null;
    }

    @Override
    public void reduce(TotSS other) {
      _tss += other._tss;
    }
  }
}