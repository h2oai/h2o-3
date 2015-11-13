package hex;

import water.Key;

/** Clustering Model
 *  Generates a 2-D array of clusters.
 */
public abstract class ClusteringModel<M extends ClusteringModel<M,P,O>, P extends ClusteringModel.ClusteringParameters, O extends ClusteringModel.ClusteringOutput> extends Model<M,P,O> {

  public ClusteringModel( Key selfKey, P parms, O output ) { super(selfKey,parms,output);  }

  /** Clustering Model Parameters includes the number of clusters desired */
  public abstract static class ClusteringParameters extends Model.Parameters {
    /** Clustering models must specify the number of clusters to generate */
    public int _k = 1;
  }

  /** Output from all Clustering Models, includes generated clusters */
  public abstract static class ClusteringOutput extends Model.Output {
    /** Cluster centers_raw.  During model init, might be null or might have a "k"
    *   which is oversampled a lot.  Not standardized (although if standardization
    *   is used during the building process, the *builders* cluster centers_raw are standardized). */
    public double[/*k*/][/*features*/] _centers_raw;
    public double[/*k*/][/*features*/] _centers_std_raw;
    // For internal use only: means and 1/(std dev) of each training col
    public double[] _normSub;
    public double[] _normMul;

    public ClusteringOutput() {
      this(null);
    }

    /** Any final prep-work just before model-building starts, but after the
     *  user has clicked "go". */
    public ClusteringOutput(ClusteringModelBuilder b) { super(b); }

    @Override public boolean isSupervised() { return false; }

    // Output classes is weird for clustering - it's like a regression
    public int nclasses() { return 1; }

    @Override public ModelCategory getModelCategory() { return ModelCategory.Clustering; }
  }

}
