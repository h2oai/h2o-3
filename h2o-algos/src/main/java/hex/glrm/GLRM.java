package hex.glrm;

import hex.DataInfo;
import hex.DataInfo.Row;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram.*;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.GLRMV2;
import hex.gram.Gram.GramTask;
import hex.FrameTask;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Log;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * Generalized Low Rank Models
 * This is an algorithm for dimensionality reduction of numerical data.
 * It is a general, parallelized implementation of PCA with regularization.
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 *
 */
public class GLRM extends ModelBuilder<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  static final int MAX_COL = 5000;

  public enum Initialization {
    SVD, PlusPlus
  }

  @Override
  public ModelBuilderSchema schema() {
    return new GLRMV2();
  }

  @Override
  public Job<GLRMModel> trainModel() {
    return start(new GLRMDriver(), 0);
  }

  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{ Model.ModelCategory.Clustering };
  }

  // Called from an http request
  public GLRM(GLRMModel.GLRMParameters parms ) { super("GLRM",parms); init(false); }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if(_train.numCols() < 2) error("_train", "_train must have more than one column");
    if(_parms._num_pc > _train.numCols()) error("_num_pc", "_num_pc cannot be greater than the number of columns in _train");
    if(_parms._gamma < 0) error("_gamma", "lambda must be a non-negative number");

    Vec[] vecs = _train.vecs();
    for(int i = 0; i < vecs.length; i++) {
      if(!vecs[i].isNumeric()) throw H2O.unimpl();
    }
  }

  private class GLRMDriver extends H2O.H2OCountedCompleter<GLRMDriver> {
    @Override
    protected void compute2() {
      Frame x;
      GLRMModel model = null;
      DataInfo dinfo, xinfo, axinfo, ayinfo;
      Key xkey = Key.make();
      Key axkey = Key.make();
      Key aykey = Key.make();

      try {
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        init(true);
        if( error_count() > 0 ) throw new IllegalArgumentException("Found validation errors: "+validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(_key);

        // TODO: Initialize Y' matrix using k-means++
        double[][] yt = new double[_train.numCols()][_parms._num_pc];
        for(int i = 0; i < yt.length; i++) Arrays.fill(yt[i], 0);

        // TODO: Put this in while loop that ends when error < tolerance or over max iterations
        // 1) Compute X = AY'(YY' + \gamma I)^(-1)
        // a) Form transpose of Gram matrix (YY')' = Y'Y
        double[][] ygram = new double[_parms._num_pc][_parms._num_pc];
        for(int i = 0; i < yt.length; i++) {
          // Outer product = yt[i]' * yt[i]
          for(int j = 0; j < yt[i].length; j++) {
            for(int k = 0; k < yt[i].length; k++)
              ygram[k][j] += yt[i][j]*yt[i][k];
          }
        }

        // b) Get Cholesky decomposition of D = Y'Y + \gamma I
        double[] diag = new double[_parms._num_pc];
        if(_parms._gamma > 0) {
          for (int i = 0; i < ygram.length; i++) {
            ygram[i][i] += _parms._gamma;
            diag[i] = ygram[i][i];
          }
        }
        // CholeskyDecomposition yychol = new Matrix(ygram).chol();
        Cholesky yychol = new Cholesky(ygram, diag);

        // c) Compute AY' and solve for X of XD = AY'
        dinfo = new DataInfo(_train._key, _train, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        BMulTask mtsk = new BMulTask(self(), dinfo, _parms._num_pc, yt).doAll(_parms._num_pc, dinfo._adaptedFrame);
        Frame ay = mtsk.outputFrame(aykey, null, null);
        DKV.put(ay);
        // ay.unlock(self());

        // Solve for X of XD = AY' -> D'X' = YA' using distributed Cholesky
        ayinfo = new DataInfo(ay._key, ay, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        CholTask ctsk = new CholTask(self(), ayinfo, yychol, _parms._num_pc).doAll(_parms._num_pc, ayinfo._adaptedFrame);
        x = ctsk.outputFrame(xkey, null, null);
        DKV.put(x);

        // 2) Compute Y = (X'X + \gamma I)^(-1)X'A
        // a) Form Gram matrix X'X and add \gamma to diagonal
        xinfo = new DataInfo(x._key, x, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        GramTask xgram = new GramTask(xkey, xinfo).doAll(xinfo._adaptedFrame);
        if(_parms._gamma > 0) xgram._gram.addDiag(_parms._gamma);

        // b) Get Cholesky decomposition of D = X'X + \gamma I
        Cholesky xxchol = xgram._gram.cholesky(null);

        // c) Compute A'X and solve for Y' of DY' = A'X
        // Jam A and X into single frame [A,X] for distributed computation
        Vec[] vecs = new Vec[_train.numCols() + _parms._num_pc];
        for(int i = 0; i < _train.numCols(); i++) vecs[i] = _train.vec(i);
        for(int i = _train.numCols(); i < vecs.length; i++) vecs[i] = x.vec(i);
        Frame ax = new Frame(axkey, null, vecs);
        axinfo = new DataInfo(ax._key, ax, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);

        yt = new SMulTask(_train.numCols(), _parms._num_pc).doAll(axinfo._adaptedFrame)._prod;
        for(int i = 0; i < yt.length; i++) xxchol.solve(yt[i]);

        // TODO: Compute solution XY
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }

    Key self() { return _key; }
  }

  // Computes A'X on a matrix [A, X], where A is n by p, X is n by k, and k <= p
  // Resulting matrix D = A'X will have dimensions p by k
  private static class SMulTask extends MRTask<SMulTask> {
    int _ncolA, _ncolX; // _ncolA = p, _ncolX = k
    double[][] _prod;   // _prod = D = A'X

    SMulTask(final int ncolA, final int ncolX) {
      _ncolA = ncolA;
      _ncolX = ncolX;
      _prod = new double[ncolX][ncolA];
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + _ncolX) == cs.length;

      // Cycle over columns of A
      for( int i = 0; i < _ncolA; i++ ) {
        // Cycle over columns of X
        for(int j = _ncolA; j < _ncolX; j++ ) {
          double sum = 0;
          for( int row = 0; row < cs[0]._len; row++ ) {
            double a = cs[i].atd(row);
            double x = cs[j].atd(row);
            if(Double.isNaN(a) || Double.isNaN(x)) continue;
            sum += a*x;
          }
          _prod[i][j] = sum;
        }
      }
    }

    @Override public void reduce(SMulTask other) {
      ArrayUtils.add(_prod, other._prod);
    }
  }

  // TODO: Generalize this with boolean flag indicating whether to transpose Y
  // Computes AY' where A is n by p, Y is k by p, and k <= p
  // Resulting matrix D = AY' will have dimensions n by k
  private static class BMulTask extends FrameTask<BMulTask> {
    int _ncomp;       // _ncomp = k (number of PCs)
    double[][] _yt;   // _yt = Y' (transpose of Y)

    BMulTask(Key jobKey, DataInfo dinfo, final int ncomp, final double[][] yt) {
      super(jobKey, dinfo);
      _ncomp = ncomp;
      _yt = yt;
    }

    @Override protected void processRow(long gid, Row row, NewChunk[] outputs) {
      double[] nums = row.numVals;
      for (int k = 0; k < _ncomp; k++) {
        double x = 0;
        int c = _dinfo.numStart();
        for(int d = 0; d < nums.length; d++)
          x += nums[d] * _yt[c++][k];
        assert c == _yt.length;
        outputs[k].addNum(x);
      }
    }
  }

  // Solves XD = Y -> D'X' = Y' for X where D is k by k, Y is n by k, and n >> k
  // Resulting matrix X = YD^(-1) will have dimensions n by k
  private static class CholTask extends FrameTask<CholTask> {
    int _ncomp;
    Cholesky _chol;   // Cholesky decomposition of D' (transpose since left multiply)

    CholTask(Key jobKey, DataInfo dinfo, final Cholesky chol, final int ncomp) {
      super(jobKey, dinfo);
      _chol = chol;
      _ncomp = ncomp;
    }

    @Override protected void processRow(long gid, Row row, NewChunk[] outputs) {
      double[] ynums = row.numVals.clone();
      assert ynums.length == _ncomp;
      _chol.solve(ynums);
      for(int k = 0; k < _ncomp; k++)
        outputs[k].addNum(ynums[k]);
    }
  }
}
