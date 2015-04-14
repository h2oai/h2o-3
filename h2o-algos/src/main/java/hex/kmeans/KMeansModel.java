package hex.kmeans;

import hex.ClusteringModel;
import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import water.Key;
import water.fvec.Frame;
import water.util.JCodeGen;
import water.util.SB;
import water.util.TwoDimTable;

public class KMeansModel extends ClusteringModel<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends ClusteringModel.ClusteringParameters {
    public int _max_iterations = 1000;     // Max iterations
    public boolean _standardize = true;    // Standardize columns
    public long _seed = System.nanoTime(); // RNG seed
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
    Key<Frame> _user_points;
  }

  public static class KMeansOutput extends ClusteringModel.ClusteringOutput {
    /** Cluster centers built on standardized data. Null if standardize = false.
     *  During model init, might be null or might have a "k" which is oversampled a lot. */
    public TwoDimTable _centers_std;    // Row = cluster ID, Column = feature
    public double[/*k*/][/*features*/] _centers_std_raw;
    public double[/*k*/][/*features*/] _centers_raw;

    // Iterations executed
    public int _iterations;

    // Compute average change in standardized cluster centers
    public double[/*iterations*/] _avg_centroids_chg = new double[]{Double.NaN};

    // Sum squared distance between each point and its cluster center, divided by total observations in cluster.
    public double[/*k*/] _within_mse;   // Within-cluster MSE, variance

    // Sum squared distance between each point and its cluster center, divided by total number of observations.
    public double _avg_within_ss;      // Average within-cluster sum-of-square error
    public double[/*iterations*/] _history_avg_within_ss = new double[0];

    // Sum squared distance between each point and grand mean, divided by total number of observations.
    public double _avg_ss;            // Total MSE to grand mean centroid

    // Sum squared distance between each cluster center and grand mean, divided by total number of observations.
    public double _avg_between_ss;    // Total between-cluster MSE (avgss - avgwithinss)

    // For internal use only: means and 1/(std dev) of each training col
    public double[] _normSub;
    public double[] _normMul;

    public KMeansOutput( KMeans b ) { super(b); }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    return new ModelMetricsClustering.MetricBuilderClustering(_output.nfeatures(),_parms._k);
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    double[][] centers = _parms._standardize ? _output._centers_std_raw : _output._centers_raw;
    preds[0] = hex.genmodel.GenModel.KMeans_closest(centers,data,_output._domains,_output._normSub,_output._normMul);
    return preds;
  }

  // Override in subclasses to provide some top-level model-specific goodness
  @Override protected void toJavaPredictBody(SB bodySb, SB classCtxSb, SB fileCtxSb) {
    // fileCtxSb.ip("").nl(); // at file level
    // Two class statics to support prediction
    if(_parms._standardize) {
      JCodeGen.toStaticVar(classCtxSb,"MEANS",_output._normSub,"Column means of training data");
      JCodeGen.toStaticVar(classCtxSb,"MULTS",_output._normMul,"Reciprocal of column standard deviations of training data");
      JCodeGen.toStaticVar(classCtxSb, "CENTERS", _output._centers_std_raw, "Normalized cluster centers[K][features]");
      // Predict function body: main work function is a utility in GenModel class.
      bodySb.ip("preds[0] = KMeans_closest(CENTERS,data,DOMAINS,MEANS,MULTS);").nl(); // at function level
    } else {
      JCodeGen.toStaticVar(classCtxSb, "CENTERS", _output._centers_raw, "Denormalized cluster centers[K][features]");
      // Predict function body: main work function is a utility in GenModel class.
      bodySb.ip("preds[0] = KMeans_closest(CENTERS,data,DOMAINS,null,null);").nl(); // at function level
    }
  }
}
