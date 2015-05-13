package hex.svd;

import hex.*;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

public class SVDModel extends Model<SVDModel,SVDModel.SVDParameters,SVDModel.SVDOutput> {
  public static class SVDParameters extends Model.Parameters {
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public int _nv = 1;    // Number of right singular vectors to calculate
    public int _max_iterations = 1000;    // Maximum number of iterations
    public long _seed = System.nanoTime();        // RNG seed
    public boolean _keep_u = true;    // Should left singular vectors be saved in memory? (Only applies if _only_v = false)
    public Key<Frame> _u_key;         // Frame key for left singular vectors (U)
    public boolean _only_v = false;   // Compute only right singular vectors? (Faster if true)
    public boolean _useAllFactorLevels = true;   // When expanding categoricals, should last level be dropped?
  }

  public static class SVDOutput extends Model.Output {
    // Right singular vectors (V)
    public double[][] _v;

    // Singular values (diagonal of D)
    public double[] _d;

    // Frame key for left singular vectors (U)
    public Key<Frame> _u_key;

    // Number of categorical and numeric columns
    public int _ncats;
    public int _nnums;

    // Categorical offset vector
    public int[] _catOffsets;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    // Permutation matrix mapping training col indices to adaptedFrame
    public int[] _permutation;

    public SVDOutput(SVD b) { super(b); }

    @Override public ModelCategory getModelCategory() { return ModelCategory.DimReduction; }
  }

  public SVDModel(Key selfKey, SVDParameters parms, SVDOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsSVD.SVDModelMetrics(_parms._nv);
  }

  public static class ModelMetricsSVD extends ModelMetricsUnsupervised {
    public ModelMetricsSVD(Model model, Frame frame) {
      super(model, frame, Double.NaN);
    }

    // SVD currently does not have any model metrics to compute during scoring
    public static class SVDModelMetrics extends MetricBuilderUnsupervised {
      public SVDModelMetrics(int dims) {
        _work = new double[dims];
      }

      @Override public double[] perRow(double[] dataRow, float[] preds, Model m) { return dataRow; }

      @Override public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) {
        return m._output.addModelMetrics(new ModelMetricsSVD(m, f));
      }
    }
  }

  @Override protected Frame scoreImpl(Frame orig, Frame adaptedFr, String destination_key) {
    Frame adaptFrm = new Frame(adaptedFr);
    for(int i = 0; i < _parms._nv; i++)
      adaptFrm.add("PC"+String.valueOf(i+1),adaptFrm.anyVec().makeZero());

    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[_output._names.length];
        double preds[] = new double[_parms._nv];
        for( int row = 0; row < chks[0]._len; row++) {
          double p[] = score0(chks, row, tmp, preds);
          for( int c=0; c<preds.length; c++ )
            chks[_output._names.length+c].set(row, p[c]);
        }
      }
    }.doAll(adaptFrm);

    // Return the projection into right singular vector (V) space
    int x = _output._names.length, y = adaptFrm.numCols();
    Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

    f = new Frame((null == destination_key ? Key.make() : Key.make(destination_key)), f.names(), f.vecs());
    DKV.put(f);
    makeMetricBuilder(null).makeModelMetrics(this, orig, Double.NaN);
    return f;
  }

  // TODO: Need permutation matrix to index into cols correctly
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    int numStart = _output._catOffsets[_output._catOffsets.length-1];
    assert data.length == _output._permutation.length;

    for(int i = 0; i < _parms._nv; i++) {
      preds[i] = 0;
      for (int j = 0; j < _output._ncats; j++) {
        int level = (int)data[_output._permutation[j]];
        preds[i] += _output._v[_output._catOffsets[j]+level][i];
      }

      int dcol = _output._ncats;
      int vcol = numStart;
      for (int j = 0; j < _output._nnums; j++) {
        preds[i] += (data[_output._permutation[dcol]] - _output._normSub[j]) * _output._normMul[j] * _output._v[vcol][i];
        dcol++; vcol++;
      }
    }
    return preds;
  }

  @Override public Frame score(Frame fr, String destination_key) {
    Frame adaptFr = new Frame(fr);
    adaptTestForTrain(adaptFr, true);   // Adapt
    Frame output = scoreImpl(fr, adaptFr, destination_key); // Score

    Vec[] vecs = adaptFr.vecs();
    for (int i = 0; i < vecs.length; i++)
      if (fr.find(vecs[i]) != -1)   // Exists in the original frame?
        vecs[i] = null;            // Do not delete it
    adaptFr.delete();
    return output;
  }
}
