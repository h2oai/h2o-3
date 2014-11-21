package hex.kmeans;

import hex.Model;
import hex.schemas.KMeansModelV2;
import water.Key;
import water.api.ModelSchema;

public class KMeansModel extends Model<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {

  public static class KMeansParameters extends Model.Parameters {
    public int _K = 2;                     // Number of clusters
    public int _max_iters = 1000;          // Max iterations
    public boolean _normalize = true;      // Normalize columns
    public long _seed = System.nanoTime(); // RNG seed
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
  }

  public static class KMeansOutput extends Model.Output {
    // Number of categorical variables in the training set; they are all moved
    // up-front and use a different distance metric than numerical variables
    public int _ncats;

    // Iterations executed
    public int _iters;

    // Cluster centers.  During model init, might be null or might have a "K"
    // which is oversampled alot.  Not normalized (although if normalization is
    // used during the building process, the *builders* clusters are normalized).
    public double[/*K*/][/*features*/] _clusters;
    // Rows per cluster
    public long[/*K*/] _rows;

    // Sum squared distance between each point and its cluster center, divided by rows
    public double[/*K*/] _mses;   // Per-cluster MSE, variance

    // Sum squared distance between each point and its cluster center, divided by rows.
    public double _mse;           // Total MSE, variance

    public KMeansOutput( KMeans b ) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for KMeans all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return Model.ModelCategory.Clustering;
    }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override
  public boolean isSupervised() {return false;}

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new KMeansModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    preds[0] = KMeans.closest(_output._clusters,data,_output._ncats);
    return preds;
  }
}
