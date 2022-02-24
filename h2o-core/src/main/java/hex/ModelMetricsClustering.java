package hex;

import hex.ClusteringModel.ClusteringOutput;
import hex.ClusteringModel.ClusteringParameters;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.ComparisonUtils;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelMetricsClustering extends ModelMetricsUnsupervised {
  public long[/*k*/] _size;
  public double[/*k*/] _withinss;
  public double _totss;
  public double _tot_withinss;
  public double _betweenss;


  public double totss() { return _totss; }
  public double tot_withinss() { return _tot_withinss; }
  public double betweenss() { return _betweenss; }

  public ModelMetricsClustering(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
    _size = null;
    _withinss = null;
    _totss = _tot_withinss = _betweenss = Double.NaN;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" total sum of squares: " + (float)_totss + "\n");
    sb.append(" total within sum of squares: " + (float)_tot_withinss + "\n");
    sb.append(" total between sum of squares: " + (float)_betweenss + "\n");
    if (_size != null) sb.append(" per cluster sizes: " + Arrays.toString(_size) + "\n");
    if (_withinss != null) sb.append(" per cluster within sum of squares: " + Arrays.toString(_withinss) + "\n");
    return sb.toString();
  }

  @Override
  public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
    super.isEqualUpToTolerance(comparator, other);
    ModelMetricsClustering specificOther = (ModelMetricsClustering) other;

    comparator.compareUpToTolerance("totss", this.totss(), specificOther.totss());
    comparator.compareUpToTolerance("tot_withinss", this.tot_withinss(), specificOther.tot_withinss());
    comparator.compareUpToTolerance("betweenss", this.betweenss(), specificOther.betweenss());
    comparator.compare("size", this._size, specificOther._size);
    comparator.compareUpToTolerance("withinss", this._withinss, specificOther._withinss);
            
    return comparator.isEqual();
  }

  /**
   * Populate TwoDimTable from members _size and _withinss
   * @return TwoDimTable
   */
  public TwoDimTable createCentroidStatsTable() {
    if (_size == null || _withinss == null)
      return null;
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();

    colHeaders.add("Centroid"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Size"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Within Cluster Sum of Squares"); colTypes.add("double"); colFormat.add("%.5f");

    final int K = _size.length;
    assert(_withinss.length == K);

    TwoDimTable table = new TwoDimTable(
            "Centroid Statistics", null,
            new String[K],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    for (int k =0; k<K; ++k) {
      int col = 0;
      table.set(k, col++, k+1);
      table.set(k, col++, _size[k]);
      table.set(k, col, _withinss[k]);
    }
    return table;
  }

  public static class MetricBuilderClustering extends MetricBuilderUnsupervised<MetricBuilderClustering> {
    public long[] _size;        // Number of elements in cluster
    public double[] _within_sumsqe;   // Within-cluster sum of squared error
    private double[/*features*/] _colSum;  // Sum of each column
    private double[/*features*/] _colSumSq;  // Sum of squared values of each column

    public MetricBuilderClustering(int ncol, int nclust) {
      _work = new double[ncol];
      _size = new long[nclust];
      _within_sumsqe = new double[nclust];
      Arrays.fill(_size, 0);
      Arrays.fill(_within_sumsqe, 0);
      _colSum = new double[ncol];
      _colSumSq = new double[ncol];
      Arrays.fill(_colSum, 0);
      Arrays.fill(_colSumSq, 0);
    }

    // Compare row (dataRow) against centroid it was assigned to (preds[0])
    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      assert m instanceof ClusteringModel;
      assert !Double.isNaN(preds[0]);

      ClusteringModel clm = (ClusteringModel) m;
      boolean standardize = ((((ClusteringOutput) clm._output)._centers_std_raw) != null);
      double[][] centers = standardize ? ((ClusteringOutput) clm._output)._centers_std_raw: ((ClusteringOutput) clm._output)._centers_raw;

      int clus = (int)preds[0];
      double [] colSum = new double[_colSum.length];
      double [] colSumSq = new double[_colSumSq.length];
      double sqr = hex.genmodel.GenModel.KMeans_distance(centers[clus], dataRow, ((ClusteringOutput) clm._output)._mode, colSum, colSumSq);
      ArrayUtils.add(_colSum, colSum);
      ArrayUtils.add(_colSumSq, colSumSq);
      _count++;
      _size[clus]++;
      _sumsqe += sqr;
      _within_sumsqe[clus] += sqr;

      if (Double.isNaN(_sumsqe))
        throw new H2OIllegalArgumentException("Sum of Squares is invalid (Double.NaN) - Check for missing values in the dataset.");
      return preds;                // Flow coding
    }

    @Override
    public void reduce(MetricBuilderClustering mm) {
      super.reduce(mm);
      ArrayUtils.add(_size, mm._size);
      ArrayUtils.add(_within_sumsqe, mm._within_sumsqe);
      ArrayUtils.add(_colSum, mm._colSum);
      ArrayUtils.add(_colSumSq, mm._colSumSq);
    }

    /**
     * Reduce the Metric builder clustering for CV without cluster statistics.
     *
     * @param mm metric builder to be reduced
     */
    public void reduceForCV(MetricBuilderClustering mm) {
      super.reduce(mm);
      ArrayUtils.add(_colSum, mm._colSum);
      ArrayUtils.add(_colSumSq, mm._colSumSq);
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      assert m instanceof ClusteringModel;
      ModelMetricsClustering mm = new ModelMetricsClustering(m, f, _customMetric);
      setOverallStatToModelMetrics((ClusteringModel) m, f.numRows(), mm);
      if(this._size != null && this._within_sumsqe != null) {
        setCentroidsStatToModelMetrics(mm);
      }
      return m.addMetrics(mm);
    }
    
    private void setOverallStatToModelMetrics(ClusteringModel clm, long numRows, ModelMetricsClustering mm){
      mm._tot_withinss = _sumsqe;

      if (clm._parms._weights_column != null) numRows = _count;

      // Sum-of-square distance from grand mean
      if (((ClusteringParameters) clm._parms)._k == 1)
        mm._totss = mm._tot_withinss;
      else {
        mm._totss = 0;
        for (int i = 0; i < _colSum.length; i++) {
          if (((ClusteringOutput) clm._output)._mode[i] == -1)
            mm._totss += _colSumSq[i] - (_colSum[i] * _colSum[i]) / numRows;
          else
            mm._totss += _colSum[i]; // simply add x[i] != modes[i] for categoricals
        }
      }
      mm._betweenss = mm._totss - mm._tot_withinss;
    }
    
    private void setCentroidsStatToModelMetrics(ModelMetricsClustering mm){
      mm._size = _size;
      mm._withinss = new double[_size.length];
      for (int i = 0; i < mm._withinss.length; i++)
        mm._withinss[i] = _within_sumsqe[i];
    }
  }

  public static class IndependentMetricBuilderClustering extends IndependentMetricBuilderUnsupervised<IndependentMetricBuilderClustering> {
    public long[] _size;        // Number of elements in cluster
    public double[] _within_sumsqe;   // Within-cluster sum of squared error
    private double[/*features*/] _colSum;  // Sum of each column
    private double[/*features*/] _colSumSq;  // Sum of squared values of each column
    private double[/*k*/][/*features*/] _centers;
    private int[] _mode;
    private int _k;

    public IndependentMetricBuilderClustering() {}
            
    public IndependentMetricBuilderClustering(
        int ncol,
        int k,
        double[][] centers,
        int[] mode) {
      _work = new double[ncol];
      _size = new long[k];
      _within_sumsqe = new double[k];
      Arrays.fill(_size, 0);
      Arrays.fill(_within_sumsqe, 0);
      _colSum = new double[ncol];
      _colSumSq = new double[ncol];
      Arrays.fill(_colSum, 0);
      Arrays.fill(_colSumSq, 0);
      _centers = centers;
      _mode = mode;
      _k = k;
    }

    // Compare row (dataRow) against centroid it was assigned to (preds[0])
    @Override
    public double[] perRow(double[] preds, float[] dataRow) {
      assert !Double.isNaN(preds[0]);
      

      int clus = (int)preds[0];
      double [] colSum = new double[_colSum.length];
      double [] colSumSq = new double[_colSumSq.length];
      double sqr = hex.genmodel.GenModel.KMeans_distance(_centers[clus], dataRow, _mode, colSum, colSumSq);
      // System.out.println(Arrays.toString(colSumSq));
      ArrayUtils.add(_colSum, colSum);
      ArrayUtils.add(_colSumSq, colSumSq);
      _count++;
      _size[clus]++;
      _sumsqe += sqr;
      _within_sumsqe[clus] += sqr;

      if (Double.isNaN(_sumsqe))
        throw new H2OIllegalArgumentException("Sum of Squares is invalid (Double.NaN) - Check for missing values in the dataset.");
      return preds;                // Flow coding
    }

    @Override
    public void reduce(IndependentMetricBuilderClustering mm) {
      super.reduce(mm);
      ArrayUtils.add(_size, mm._size);
      ArrayUtils.add(_within_sumsqe, mm._within_sumsqe);
      ArrayUtils.add(_colSum, mm._colSum);
      ArrayUtils.add(_colSumSq, mm._colSumSq);
    }

    @Override
    public ModelMetrics makeModelMetrics() {
      ModelMetricsClustering mm = new ModelMetricsClustering(null, null, _customMetric);

      mm._size = _size;
      mm._tot_withinss = _sumsqe;
      mm._withinss = new double[_size.length];
      for (int i = 0; i < mm._withinss.length; i++)
        mm._withinss[i] = _within_sumsqe[i];

      long numRows = _count;

      // Sum-of-square distance from grand mean
      if (_k == 1)
        mm._totss = mm._tot_withinss;
      else {
        mm._totss = 0;
        for (int i = 0; i < _colSum.length; i++) {
          if (_mode[i] == -1)
            mm._totss += _colSumSq[i] - (_colSum[i] * _colSum[i]) / numRows;
          else
            mm._totss += _colSum[i]; // simply add x[i] != modes[i] for categoricals
        }
      }
      mm._betweenss = mm._totss - mm._tot_withinss;
      return mm;
    }
  }
}
