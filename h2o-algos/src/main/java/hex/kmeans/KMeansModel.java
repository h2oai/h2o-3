package hex.kmeans;

import hex.Model;
import hex.ModelMetrics;
import water.Key;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class KMeansModel extends Model<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends Model.Parameters {
    public int _k = 1;                     // Number of clusters
    public int _max_iterations = 1000;     // Max iterations
    public boolean _standardize = true;    // Standardize columns
    public long _seed = System.nanoTime(); // RNG seed
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
    Key<Frame> _user_points;
  }

  public static class KMeansOutput extends Model.Output {
    // Number of categorical variables in the training set; they are all moved
    // up-front and use a different distance metric than numerical variables
    public int _categorical_column_count;

    // Iterations executed
    public int _iterations;

    // Cluster centers_raw.  During model init, might be null or might have a "k"
    // which is oversampled a lot.  Not standardized (although if standardization
    // is used during the building process, the *builders* cluster centers_raw are standardized).
    public TwoDimTable _centers;    // Row = cluster ID, Column = feature
    public double[/*k*/][/*features*/] _centers_raw;

    // Cluster size. Defined as the number of rows in each cluster.
    public long[/*k*/] _size;

    // Sum squared distance between each point and its cluster center, divided by total observations in cluster.
    public double[/*k*/] _within_mse;   // Within-cluster MSE, variance

    // Sum squared distance between each point and its cluster center, divided by total number of observations.
    public double _avg_within_ss;      // Average within-cluster sum-of-square error

    // Sum squared distance between each point and grand mean, divided by total number of observations.
    public double _avg_ss;            // Total MSE to grand mean centroid

    // Sum squared distance between each cluster center and grand mean, divided by total number of observations.
    public double _avg_between_ss;    // Total between-cluster MSE (avgss - avgwithinss)

    // For internal use only: means and 1/(std dev) of each training col
    public double[] _normSub;
    public double[] _normMul;

    public KMeansOutput( KMeans b ) { super(b); }

    @Override public ModelCategory getModelCategory() {
      return Model.ModelCategory.Clustering;
    }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    return new ModelMetricsKMeans.MetricBuilderKMeans(_output.nfeatures(),_parms._k);
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    preds[0] = KMeans.closest(_output._centers_raw,data,_output._categorical_column_count);
    return preds;
  }
}
