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
    private double[/*features*/] _colSum;  // Sum of each column
    private double[/*features*/] _colSumSq;  // Sum of squared values of each column

    public MetricBuilderKMeans(int ncol, int nclust) {
      _size = new long[nclust];
      _within_sumsqe = new double[nclust];
      Arrays.fill(_size, 0);
      Arrays.fill(_within_sumsqe, 0);
      _work = new float[ncol];

      _colSum = new double[ncol];
      _colSumSq = new double[ncol];
      Arrays.fill(_colSum, 0);
      Arrays.fill(_colSumSq, 0);
    }

    // FIXME: Is adapted frame (dataRow) standardized when model built on standardized data?
    // Compare row (dataRow) against centroid it was assigned to (preds[0])
    @Override
    public float[] perRow(float[] preds, float[] dataRow, Model m) {
      assert m instanceof KMeansModel;
      if (Float.isNaN(preds[0])) return dataRow; // No errors if prediction  is missing
      if (Float.isNaN(dataRow[0])) return dataRow; // No errors if actual is missing

      final TwoDimTable centers = ((KMeansModel) m)._output._centers;
      assert (dataRow.length == centers.getColDim());
      final int clus = (int) preds[0];   // Assigned cluster index
      assert 0 <= clus && clus < _within_sumsqe.length;

      // Compute error
      for (int i = 0; i < dataRow.length; ++i) {
        double err = (double) centers.get(clus, i) - dataRow[i]; // Error: distance from assigned cluster center
        _sumsqe += err * err;       // Squared error
        _within_sumsqe[clus] += err * err;

        _colSum[i] += dataRow[i];
        _colSumSq[i] += dataRow[i] * dataRow[i];
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

      ArrayUtils.add(_colSum, mm._colSum);
      ArrayUtils.add(_colSumSq, mm._colSumSq);
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

      // Sum-of-square distance from grand mean
      if(km._parms._standardize)
        mm._avg_ss = f.numCols()*(f.numRows()-1) / f.numRows();
      else if (km._parms._k == 1)
        mm._avg_ss = mm._avg_within_ss;
      else {
        mm._avg_ss = 0;
        for (int i = 0; i < _colSum.length; i++)
          mm._avg_ss += _colSumSq[i] - (_colSum[i] * _colSum[i]) / f.numRows();
        mm._avg_ss /= f.numRows();
      }
      mm._avg_between_ss = mm._avg_ss - mm._avg_within_ss;
      return m.addMetrics(mm);
    }
  }
}