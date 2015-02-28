package hex.genmodel;

import java.util.Map;

/** This is a helper class to support Java generated models. */
public abstract class GenModel {
  public static enum ModelCategory {
    Unknown,
    Binomial,
    Multinomial,
    Regression,
    Clustering,
    AutoEncoder,
    DimReduction
  }

  /** Column names; last is response for supervised models */
  public final String[] _names; 

  /** Categorical/factor/enum mappings, per column.  Null for non-enum cols.
   *  Columns match the post-init cleanup columns.  The last column holds the
   *  response col enums for SupervisedModels.  */
  public final String _domains[][];

  public GenModel( String[] names, String domains[][] ) { _names = names; _domains = domains; }

  // Base methods are correct for unsupervised models.  All overridden in GenSupervisedModel
  public boolean isSupervised() { return false; }  // Overridden in GenSupervisedModel
  public int nfeatures() { return _names.length; } // Overridden in GenSupervisedModel
  public int nclasses() { return 0; }              // Overridden in GenSupervisedModel

  abstract public ModelCategory getModelCategory();

  /** Takes a HashMap mapping column names to doubles.
   *  <p>
   *  Looks up the column names needed by the model, and places the doubles into
   *  the data array in the order needed by the model. Missing columns use NaN.
   *  </p>
   */
  public double[] map( Map<String, Double> row, double data[] ) {
    String[] colNames = _names;
    for( int i=0; i<nfeatures(); i++ ) {
      Double d = row.get(colNames[i]);
      data[i] = d==null ? Double.NaN : d;
    }
    return data;
  }

  
  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  This call
   *  exactly matches the hex.Model.score0, but uses the light-weight
   *  GenModel class. */
  abstract protected float[] score0( double[] data, float[] preds );

  // Does the mapping lookup for every row, no allocation.
  // data and preds arrays are pre-allocated and can be re-used for every row.
  public float[] score0( Map<String, Double> row, double data[], float preds[] ) {
    return score0(map(row,data),preds);
  }

  // Does the mapping lookup for every row.
  // preds array is pre-allocated and can be re-used for every row.
  // Allocates a double[] for every row.
  public float[] score0( Map<String, Double> row, float preds[] ) {
    return score0(map(row,new double[nfeatures()]),preds);
  }

  // Does the mapping lookup for every row.
  // Allocates a double[] and a float[] for every row.
  public float[] score0( Map<String, Double> row ) {
    return score0(map(row,new double[nfeatures()]),new float[nclasses()+1]);
  }

}
