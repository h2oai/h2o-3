package hex.glrm;

import hex.DataInfo;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.Key;
import water.MemoryManager;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Random;

/**
 * Created by anqi_fu on 4/10/15.
 */
public class GLRMInit {
  // Convergence tolerance
  private final double TOLERANCE = 1e-8;
  private transient GLRMParameters _parms;
  private transient int _ncols;

  GLRMInit(GLRMParameters parms) {
    _parms = parms;
    _ncols = _parms._train.get().numCols();
  }

  GLRMInit(GLRMParameters parms, Frame train) {
    _parms = parms;
    _ncols = train.numCols();
  }

  // Squared Frobenius norm of a matrix (sum of squared entries)
  public static double frobenius2(double[][] x) {
    if(x == null) return 0;

    double frob = 0;
    for(int i = 0; i < x.length; i++) {
      for(int j = 0; j < x[0].length; j++)
        frob += x[i][j] * x[i][j];
    }
    return frob;
  }

  // Transform each column of a 2-D array
  public static double[][] transform(double[][] centers, int ncats, double[] normSub, double[] normMul) {
    int K = centers.length;
    int N = centers[0].length;
    double[][] value = new double[K][N];
    double[] means = normSub == null ? MemoryManager.malloc8d(N) : normSub;
    double[] mults = normMul == null ? MemoryManager.malloc8d(N) : normMul;

    for (int clu = 0; clu < K; clu++) {
      System.arraycopy(centers[clu], 0, value[clu], 0, N);
      for (int col = ncats; col < N; col++)
        value[clu][col] = (value[clu][col] - means[col]) * mults[col];
    }
    return value;
  }

  // Initialize Y to be the k centers from k-means++
  public double[][] initialY() {
    double[][] centers;

    if (null != _parms._user_points) { // User-specified starting points
      centers = new double[_parms._k][_ncols];
      Vec[] centersVecs = _parms._user_points.get().vecs();

      // Get the centers and put into array
      for (int r = 0; r < _parms._k; r++) {
        for (int c = 0; c < _ncols; c++)
          centers[r][c] = centersVecs[c].at(r);
      }
      if (frobenius2(centers) == 0)
        throw new H2OIllegalArgumentException("The user-specified points cannot all be zero");

    } else {  // Run k-means++ and use resulting cluster centers as initial Y
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = _parms._train;
      parms._ignored_columns = _parms._ignored_columns;
      parms._dropConsCols = _parms._dropConsCols;
      parms._dropNA20Cols = _parms._dropNA20Cols;
      parms._max_confusion_matrix_size = _parms._max_confusion_matrix_size;
      parms._score_each_iteration = _parms._score_each_iteration;
      parms._init = KMeans.Initialization.PlusPlus;
      parms._k = _parms._k;
      parms._max_iterations = _parms._max_iterations;
      parms._standardize = true;
      parms._seed = _parms._seed;

      KMeansModel km = null;
      KMeans job = null;
      try {
        job = new KMeans(parms);
        km = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
        if (km != null) km.remove();
      }

      // K-means automatically destandardizes centers! Need the original standardized version
      centers = transform(km._output._centers_raw, 0, km._output._normSub, km._output._normMul);
      if(frobenius2(centers) == 0) centers = ArrayUtils.gaussianArray(_parms._k, _ncols);
    }
    return centers;
  }

  public double[][] powerSVD(Key key, DataInfo dinfo, long seed) {
    // 1) Compute Gram of training data
    GramTask tsk = new GramTask(key, dinfo).doAll(dinfo._adaptedFrame);
    double[][] gram = tsk._gram.getXX();
    double[][] rsval = new double[_parms._k][gram.length];

    // 2) Compute and save first k singular values
    for(int i = 0; i < _parms._k; i++) {
      rsval[i] = powerLoop(gram, seed);

      // Calculate I - v_iv_i' using current singular value
      double[][] ivv = ArrayUtils.outerProduct(rsval[i], rsval[i]);
      for(int j = 0; j < ivv.length; j++) ivv[j][j] = 1 - ivv[j][j];

      // TODO: Update training frame A <- A - \sigma_i u_iv_i' = A - v_iv_i' = A(I - v_iv_i')
      // TODO: This gives Gram matrix A'A <- (I - v_iv_i')A'A(I - v_iv_i')
    }
    return rsval;
  }

  public double[] powerLoop(double[][] gram) {
    return powerLoop(gram, ArrayUtils.gaussianVector(gram[0].length));
  }
  public double[] powerLoop(double[][] gram, long seed) {
    return powerLoop(gram, ArrayUtils.gaussianVector(gram[0].length, seed));
  }
  public double[] powerLoop(double[][] gram, double[] vinit) {
    assert gram.length == gram[0].length;
    assert vinit.length == gram.length;

    // Set x_i to initial value x_0
    int iters = 0;
    double err = 2 * TOLERANCE;
    double[] v = vinit.clone();
    double[] vnew = new double[v.length];

    // Update x_i <- A'Ax_{i-1} where A'A = Gram of training frame
    while(err > TOLERANCE && iters < _parms._max_iterations) {
      err = 0;
      for (int i = 0; i < v.length; i++) {
        vnew[i] = ArrayUtils.innerProduct(gram[i], v);
        double diff = vnew[i] - v[i];
        err += diff * diff;
      }
      v = vnew;
      iters++;
    }

    // Compute singular value and vector from x_i
    ArrayUtils.div(v, ArrayUtils.l2norm(v));    // v = x_i/||x_i||
    // TODO: Compute \sigma_1 = ||Av_1|| and u_1 = Av_1/\sigma_1 (optional?)
    return v;
  }
}
