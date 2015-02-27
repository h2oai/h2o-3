package hex.pca;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import hex.ModelMetricsUnsupervised.MetricBuilderUnsupervised;
import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.api.ModelSchema;
import hex.schemas.PCAModelV2;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.Arrays;

public class PCAModel extends Model<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {

  public static class PCAParameters extends Model.Parameters {
    public int _k = 1;                // Number of principal components
    public double _gamma = 0;         // Regularization
    public int _max_iterations = 1000;     // Max iterations
    public long _seed = System.nanoTime(); // RNG seed
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public PCA.Initialization _init = PCA.Initialization.PlusPlus;
    public Key<Frame> _user_points;
    public Key<Frame> _loading_key;
  }

  public static class PCAOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Average change in objective function this iteration
    public double _avg_change_obj;

    // Final loading matrix (X)
    // public Frame _loadings;

    // Mapping from training data to lower dimensional k-space (Y)
    public double[][] _archetypes;

    // PCA output on XY
    // Principal components (eigenvectors) from SVD of XY
    public double[/*feature*/][/*k*/] _eigenvectors_raw;
    public TwoDimTable _eigenvectors;

    // Standard deviation of each principal component
    public double[] _std_deviation;

    // Importance of principal components
    // Standard deviation, proportion of variance explained, and cumulative proportion of variance explained
    public TwoDimTable _pc_importance;

    // Model parameters
    PCAParameters _parameters;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    public PCAOutput(PCA b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for PCA all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public PCAModel(Key selfKey, PCAParameters parms, PCAOutput output) { super(selfKey,parms,output); }

  @Override
  public boolean isSupervised() { return false; }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new PCAModelMetrics(_parms._k);
  }

  public ModelSchema schema() {
    return new PCAModelV2();
  }

  // PCA currently does not have any model metrics to compute during scoring
  public static class PCAModelMetrics extends MetricBuilderUnsupervised {
    public PCAModelMetrics(int dims) {
      _work = new float[dims];
    }

    @Override
    public float[] perRow(float[] dataRow, float[] preds, Model m) { return dataRow; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) {
      return m._output.addModelMetrics(new ModelMetricsUnsupervised(m, f));
    }
  }

  @Override
  protected Frame scoreImpl(Frame orig, Frame adaptedFr, String destination_key) {
    Frame adaptFrm = new Frame(adaptedFr);
    for(int i = 0; i < _parms._k; i++)
      adaptFrm.add("PC"+String.valueOf(i+1),adaptFrm.anyVec().makeZero());

    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[_output._names.length];
        float preds[] = new float [_parms._k];
        for( int row = 0; row < chks[0]._len; row++) {
          float p[] = score0(chks, row, tmp, preds);
          for( int c=0; c<preds.length; c++ )
            chks[_output._names.length+c].set(row, p[c]);
        }
      }
    }.doAll(adaptFrm);

    // Return the projection into principal component space
    int x = _output._names.length, y = adaptFrm.numCols();
    Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

    if (destination_key != null) {
      Key k = Key.make(destination_key);
      f = new Frame(k, f.names(), f.vecs());
      DKV.put(k, f);
    }
    makeMetricBuilder(null).makeModelMetrics(this,f,Double.NaN);
    return f;
  }

  // TODO: Use _normMul and _normSub to standardize test data?
  @Override
  protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    assert data.length == _output._eigenvectors.getRowDim();
    for(int i = 0; i < _parms._k; i++) {
      preds[i] = 0;
      for (int j = 0; j < data.length; j++)
        preds[i] += data[j] * (double)_output._eigenvectors.get(j,i);
    }
    return preds;
  }

  @Override
  public Frame score(Frame fr, String destination_key) {
    Frame adaptFr = new Frame(fr);
    adaptTestForTrain(adaptFr, true);   // Adapt
    Frame output = scoreImpl(fr, adaptFr, destination_key); // Score

    Vec[] vecs = adaptFr.vecs();
    for (int i = 0; i < vecs.length; i++)
      if (fr.find(vecs[i]) != -1) // Exists in the original frame?
        vecs[i] = null;            // Do not delete it
    adaptFr.delete();
    return output;
  }
}
