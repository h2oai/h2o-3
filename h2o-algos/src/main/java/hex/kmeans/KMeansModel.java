package hex.kmeans;

import hex.Model;
import hex.schemas.KMeansModelV2;
import water.Key;
import water.api.ModelSchema;
import water.util.TwoDimTable;
import water.fvec.Frame;

public class KMeansModel extends Model<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends Model.Parameters {
    public int _k;                         // Number of clusters
    public int _max_iters = 1000;          // Max iterations
    public boolean _standardize = true;    // Standardize columns
    public long _seed = System.nanoTime(); // RNG seed
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
    Key<Frame> _user_points;
  }

  public static class KMeansOutput extends Model.Output {
    // Number of categorical variables in the training set; they are all moved
    // up-front and use a different distance metric than numerical variables
    public int _ncats;

    // Iterations executed
    public int _iters;

    // Cluster centers.  During model init, might be null or might have a "k"
    // which is oversampled a lot.  Not standardized (although if standardization
    // is used during the building process, the *builders* cluster centers are standardized).
    public double[/*k*/][/*features*/] _centers;
      public TwoDimTable _centers2d;

    // Cluster size. Defined as the number of rows in each cluster.
    public long[/*k*/] _size;

    // Sum squared distance between each point and its cluster center, divided by total observations in cluster.
    public double[/*k*/] _withinmse;   // Within-cluster MSE, variance

    // Sum squared distance between each point and its cluster center, divided by total number of observations.
    public double _avgwithinss;      // Average within-cluster sum-of-square error

    // Sum squared distance between each point and grand mean, divided by total number of observations.
    public double _avgss;            // Total MSE to grand mean centroid

    // Sum squared distance between each cluster center and grand mean, divided by total number of observations.
    public double _avgbetweenss;    // Total between-cluster MSE (avgss - avgwithinss)

    public KMeansOutput( KMeans b ) { super(b); }

    @Override public ModelCategory getModelCategory() {
      return Model.ModelCategory.Clustering;
    }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  // Default publicly visible Schema is V2
  @Override public ModelSchema schema() { return new KMeansModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    preds[0] = KMeans.closest(_output._centers,data,_output._ncats);
    return preds;
  }
}
