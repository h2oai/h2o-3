package hex.svd;

import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;
import hex.DataInfo;
import hex.DataInfo.Row;
import hex.FrameTask;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glrm.GLRMModel;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.svd.SVDModel.SVDParameters;
import hex.util.LinearAlgebraUtils;
import hex.util.LinearAlgebraUtils.BMulInPlaceTask;
import hex.util.LinearAlgebraUtils.BMulTask;
import hex.util.LinearAlgebraUtils.SMulTask;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static hex.util.DimensionReductionUtils.createScoringHistoryTableDR;
import static hex.util.DimensionReductionUtils.transformEigenVectors;
import static java.lang.StrictMath.sqrt;
import static water.util.ArrayUtils.*;

/**
 * Singular Value Decomposition
 * <a href = "http://www.cs.yale.edu/homes/el327/datamining2013aFiles/07_singular_value_decomposition.pdf">SVD via Power Method Algorithm</a>
 * <a href = "https://www.cs.cmu.edu/~venkatg/teaching/CStheory-infoage/book-chapter-4.pdf">Proof of Convergence for Power Method</a>
 * <a href = "http://arxiv.org/pdf/0909.4061.pdf">Randomized Algorithms for Matrix Approximation</a>
 * @author anqi_fu
 */
public class SVD extends ModelBuilder<SVDModel,SVDModel.SVDParameters,SVDModel.SVDOutput> {
  // Convergence tolerance
  private final double TOLERANCE = 1e-16;    // Cutoff for estimation error of right singular vector
  private final double EPS = 1e-16;         // cutoff if vector norm is too small

  // Maximum number of columns when categoricals expanded
  private final int MAX_COLS_EXPANDED = 5000;

  private boolean _callFromGLRM;  // when SVD is used as an init method for GLRM, need to initialize properly
  private GLRMModel _glrmModel;

  // Number of columns in training set (p)
  private transient int _ncolExp;    // With categoricals expanded into 0/1 indicator cols
  boolean _wideDataset = false;         // default with wideDataset set to be false.
  private double[] _estimatedSingularValues; // store estimated singular values for power method
  private boolean _matrixRankReached = false; // stop if eigenvector norm becomes too small.  Reach rank of matrix
  private boolean _failedConvergence = false; // warn if power failed to converge for some eigenvector calculation

  @Override protected SVDDriver trainModelImpl() { return new SVDDriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.DimReduction }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  @Override public boolean isSupervised() { return false; }

  @Override public boolean havePojo() { return true; }
  @Override public boolean haveMojo() { return false; }

  // Called from an http request
  public SVD(SVDModel.SVDParameters parms         ) { super(parms    ); init(false); _glrmModel=null; _callFromGLRM=false;}
  public SVD(SVDModel.SVDParameters parms, Job job) { super(parms,job); init(false); _glrmModel=null; _callFromGLRM=false;}
  public SVD(SVDModel.SVDParameters parms, Job job, boolean callFromGlrm, GLRMModel gmodel) {
    super(parms,job);
    init(false);
    _callFromGLRM = callFromGlrm;
    if (gmodel == null)
      error("_train SVD for GLRM", "Your GLRM model parameter is null.");
    _glrmModel = gmodel;
  }
  public SVD(boolean startup_once) { super(new SVDParameters(),startup_once); }

  @Override
  protected void checkMemoryFootPrint() {
    HeartBeat hb = H2O.SELF._heartbeat;
    double p = LinearAlgebraUtils.numColsExp(_train, true);
    double r = _train.numRows();
    boolean useGramSVD = _parms._svd_method == SVDParameters.Method.GramSVD;
    boolean usePower = _parms._svd_method == SVDParameters.Method.Power;
    boolean useRandomized = _parms._svd_method == SVDParameters.Method.Randomized;
    double gramSize =  _train.lastVec().nChunks()==1 ? 1 :
            Math.log((double) _train.lastVec().nChunks()) / Math.log(2.); // gets to zero if nChunks=1
    long mem_usage = (useGramSVD || usePower || useRandomized) ? (long) (hb._cpus_allowed * p * p * 8/*doubles*/
            * gramSize) : 1; //one gram per core
    long mem_usage_w = (useGramSVD || usePower || useRandomized) ? (long) (hb._cpus_allowed * r * r * 8/*doubles*/
            * gramSize) : 1; //one gram per core
    long max_mem = hb.get_free_mem();

    if ((mem_usage > max_mem) && (mem_usage_w > max_mem)) {
      String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing the number of columns and/or the number of categorical factors.";
      error("_train", msg);
    }

    // _wideDataset is true if original memory does not fit.
    if (mem_usage > max_mem) {
      _wideDataset = true;  // have to set _wideDataset in this case
    } else {  // both ways fit into memory.  Want to choose wideDataset if p is too big.
      if ((p > 5000) && ( r < 5000)) {
        _wideDataset = true;
      }
    }
  }

  /*
		Set value of wideDataset.  Note that this routine is used for test purposes only and is not intended
		for users.
  */
  public void setWideDataset(boolean isWide) {
    _wideDataset = isWide;
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._max_iterations < 1)
      error("_max_iterations", "max_iterations must be at least 1");

    if(_train == null) return;
      if (_callFromGLRM)  // when used to initialize GLRM, need to treat binary numeric columns with binary loss as numeric columns
        _ncolExp = _glrmModel._output._catOffsets[_glrmModel._output._catOffsets.length-1]+_glrmModel._output._nnums;
      else
        _ncolExp = LinearAlgebraUtils.numColsExp(_train,_parms._use_all_factor_levels);
    if (_ncolExp > MAX_COLS_EXPANDED) {
      warn("_train", "_train has " + _ncolExp + " columns when categoricals are expanded. " +
              "Algorithm may be slow.");
    }

    if(_parms._nv < 1 || _parms._nv > _ncolExp)
      error("_nv", "Number of right singular values must be between 1 and " + _ncolExp);

    if (expensive && error_count() == 0) {
      if (!(_train.hasNAs()) || _parms._impute_missing)  {
        checkMemoryFootPrint();  // perform memory check here if dataset contains no NAs or if impute_missing enabled
      }
    }
  }

  // Compute ivv_sum - vec * vec' for symmetric array ivv_sum
  public static double[][] updateIVVSum(double[][] ivv_sum, double[] vec) {
    double diff;
    for(int i = 0; i < vec.length; i++) {
      for(int j = 0; j < i; j++) {
        diff = ivv_sum[i][j] - vec[i] * vec[j];
        ivv_sum[i][j] = ivv_sum[j][i] = diff;
      }
      ivv_sum[i][i] -= vec[i] * vec[i];
    }
    return ivv_sum;
  }

  class SVDDriver extends Driver {
    SVDModel _model;

    private double[] powerLoop(Gram gram, long seed, SVDModel model, double[] randomInitialV, double[] finalV, int k)
    {
//      Arrays.fill(randomInitialV,0);
      randomInitialV = ArrayUtils.gaussianVector(seed+k, randomInitialV); // random vector for each iteration!
      div(randomInitialV, l2norm(randomInitialV));  // normalize initial vector
      return powerLoop(gram, randomInitialV, model, finalV, k);
    }

    /**
     * Problem I have with the current powerLoop is that the err gets to be very small very quickly and
     * we never really get to iterate over much.  I am changing the stopping condition to one that is
     * used for symmetric matrices as follows:
     *
     * let X = A^m*X0, let lambda1 = AX dot_product X/(X dot_product X).
     * Stop if err = sqrt(AX dot_product AX/X dot_productX - lambda1^2) < tolerance or max iteration is reached.
     *
     * @param gram
     * @param v
     * @param model
     * @param vnew
     * @return
     */
    private double[] powerLoop(Gram gram, double[] v, SVDModel model, double[] vnew, int k) {
      // TODO: What happens if Gram matrix is essentially zero? Numerical inaccuracies in PUBDEV-1161.
      assert v.length == gram.fullN();

      // Set initial value v_0 to standard normal distribution
      int iters = 0;
      double err = 2 * TOLERANCE;
      double lambda1_calc = 0;  // this is the actual singular values that we are looking for as well!q
      double lambda_est = 0;

      int eigIndex = model._output._iterations+1; // we start counting at 1 and not zero.

      // Update v_i <- (A'Av_{i-1})/||A'Av_{i-1}|| where A'A = Gram matrix of training frame
      while(iters < _parms._max_iterations && err > TOLERANCE) {
        // Compute x_i <- A'Av_{i-1} and ||x_i||
        gram.mul(v, vnew);

        lambda1_calc = innerProduct(vnew, v);
        lambda_est = innerProduct(vnew, vnew);
        double norm = l2norm(vnew);
        double invnorm = 0;

        err = 0;
        if (norm > EPS) {   // norm is not too small
          invnorm = 1 / norm;

          for (int i = 0; i < v.length; i++) {
            vnew[i] *= invnorm;        // Compute singular vector v_i = x_i/||x_i||
            v[i] = vnew[i];         // Update v_i for next iteration
          }

          err = Math.sqrt(lambda_est - lambda1_calc * lambda1_calc);
          iters++;    // TODO: Should output vector of final iterations for each k
          // store variables for scoring history
          model._output._training_time_ms.add(System.currentTimeMillis());
          model._output._history_err.add(err);
          model._output._history_eigenVectorIndex.add((double) eigIndex);
        } else {
          _job.warn("_train SVD: Dataset is rank deficient.  User specified "+_parms._nv);
          _matrixRankReached = true;
          break;

        }
      }

      if (err > TOLERANCE) {
        _failedConvergence=true;
        _job.warn("_train: PCA Power method failed to converge within TOLERANCE.  Increase max_iterations or reduce " +
                "TOLERANCE to mitigate this problem.");
      }
      _estimatedSingularValues[k] = lambda1_calc;
      return v;
    }


    private double computeSigmaU(DataInfo dinfo, SVDModel model, int k, double[][] ivv_sum, Vec[] uvecs, double[] vresult) {
      double[] ivv_vk = ArrayUtils.multArrVec(ivv_sum, model._output._v[k], vresult);
      CalcSigmaU ctsk = new CalcSigmaU(_job._key, dinfo, ivv_vk).doAll(Vec.T_NUM, dinfo._adaptedFrame);
      model._output._d[k] = ctsk._sval;
      assert ctsk._nobs == model._output._nobs : "Processed " + ctsk._nobs + " rows but expected " + model._output._nobs;    // Check same number of skipped rows as Gram
      Frame tmp = ctsk.outputFrame();
      uvecs[k] = tmp.vec(0);   // Save output column of U
      tmp.unlock(_job);
      return model._output._d[k];
    }

/*
    // Algorithm 4.4: Randomized subspace iteration from Halk et al (http://arxiv.org/pdf/0909.4061.pdf)
    private Frame randSubIterInPlace(DataInfo dinfo, SVDModel model) {
      DataInfo yinfo = null;
      Frame yqfrm = null;

      try {
        // 1) Initialize Y = AG where G ~ N(0,1) and compute Y = QR factorization
        _job.update(1, "Initializing random subspace of training data Y");
        double[][] gt = ArrayUtils.gaussianArray(_parms._nv, _ncolExp, _parms._seed);
        RandSubInit rtsk = new RandSubInit(_job._key, dinfo, gt);
        rtsk.doAll(_parms._nv, Vec.T_NUM, dinfo._adaptedFrame);
        yqfrm = rtsk.outputFrame(Key.<Frame>make(), null, null);   // Alternates between Y and Q from Y = QR

        // Make input frame [A,Q] where A = read-only training data, Y = A \tilde{Q}, Q from Y = QR factorization
        // Note: If A is n by p (p = num cols with categoricals expanded), then \tilde{Q} is p by k and Q is n by k
        //       Q frame is used to save both intermediate Y calculation and final orthonormal Q matrix
        Frame aqfrm = new Frame(dinfo._adaptedFrame);
        aqfrm.add(yqfrm);

        // Calculate Cholesky of Y Gram to get R' = L matrix
        _job.update(1, "Computing QR factorization of Y");
        yinfo = new DataInfo(yqfrm, null, true, DataInfo.TransformType.NONE, true, false, false);
        DKV.put(yinfo._key, yinfo);
        LinearAlgebraUtils.computeQInPlace(_job._key, yinfo);

        model._output._iterations = 0;
        while (model._output._iterations < _parms._max_iterations) {
          if(stop_requested()) break;
          _job.update(1, "Iteration " + String.valueOf(model._output._iterations+1) + " of randomized subspace iteration");

          // 2) Form \tilde{Y}_j = A'Q_{j-1} and compute \tilde{Y}_j = \tilde{Q}_j \tilde{R}_j factorization
          SMulTask stsk = new SMulTask(dinfo, _parms._nv);
          stsk.doAll(aqfrm);

          Matrix ysmall = new Matrix(stsk._atq);
          QRDecomposition ysmall_qr = new QRDecomposition(ysmall);
          double[][] qtilde = ysmall_qr.getQ().getArray();

          // 3) [A,Q_{j-1}] -> [A,Y_j]: Form Y_j = A\tilde{Q}_j and compute Y_j = Q_jR_j factorization
          BMulInPlaceTask tsk = new BMulInPlaceTask(dinfo, ArrayUtils.transpose(qtilde));
          tsk.doAll(aqfrm);
          LinearAlgebraUtils.computeQInPlace(_job._key, yinfo);
          model._output._iterations++;
          model.update(_job);
        }
      } finally {
        if( yinfo != null ) yinfo.remove();
      }
      return yqfrm;
    }
*/

    // Algorithm 4.4: Randomized subspace iteration from Halk et al (http://arxiv.org/pdf/0909.4061.pdf)
    // This function keeps track of change in Q each iteration ||Q_j - Q_{j-1}||_2 to check convergence
    private Frame randSubIter(DataInfo dinfo, SVDModel model) {
      DataInfo yinfo = null;
      Frame ybig = null, qfrm = null, ysmallF = null, ysmallqfrm = null;
      final int ncolA = dinfo._adaptedFrame.numCols();
      double[][] xx = null;
      double[][] ysmall_q = null;
      DataInfo ysmallInfo = null;

      try {
        // 1) Initialize Y = AG where G ~ N(0,1) and compute Y = QR factorization
        _job.update(1, "Initializing random subspace of training data Y");
        double[][] gt = ArrayUtils.gaussianArray(_parms._nv, _ncolExp, _parms._seed);
        RandSubInit rtsk = new RandSubInit(_job._key, dinfo, gt);
        rtsk.doAll(_parms._nv, Vec.T_NUM, dinfo._adaptedFrame);
        ybig = rtsk.outputFrame(Key.<Frame>make(), null, null);
        Frame yqfrm = new Frame(ybig);
        for (int i = 0; i < _parms._nv; i++)
          yqfrm.add("qcol_" + i, yqfrm.anyVec().makeZero());

        // Calculate Cholesky of Gram to get R' = L matrix
        _job.update(1, "Computing QR factorization of Y");
        yinfo = new DataInfo(ybig, null, true, DataInfo.TransformType.NONE, true, false, false);
        DKV.put(yinfo._key, yinfo);
         LinearAlgebraUtils.computeQ(_job._key, yinfo, yqfrm, xx);

        if (yqfrm.hasInfs()) {  // dataset is rank deficient, reduce _nv to fit the true rank better
          _matrixRankReached=true;  // count when bad infinity or NaNs appear to denote problem;
          String warnMessage = "_train SVD: Dataset is rank deficient.  _parms._nv was "+_parms._nv;
          for (int colIndex = ybig.numCols(); colIndex < yqfrm.numCols(); colIndex++) {
            if (yqfrm.vec(colIndex).pinfs() > 0) {
              _parms._nv = colIndex-ybig.numCols();
              break;
            }
          }
          _job.warn(warnMessage+" and is now set to "+_parms._nv);
          // redo with correct _nv number
          gt = ArrayUtils.gaussianArray(_parms._nv, _ncolExp, _parms._seed);
          rtsk = new RandSubInit(_job._key, dinfo, gt);
          rtsk.doAll(_parms._nv, Vec.T_NUM, dinfo._adaptedFrame);
          ybig.remove();
          yinfo.remove();
          ybig = rtsk.outputFrame(Key.<Frame>make(), null, null);
          yinfo = new DataInfo(ybig, null, true, DataInfo.TransformType.NONE, true, false, false);
          DKV.put(yinfo._key, yinfo);
        }
        // Make input frame [A,Q,Y] where A = read-only training data, Y = A \tilde{Q}, Q from Y = QR factorization
        // Note: If A is n by p (p = num cols with categoricals expanded), then \tilde{Q} is p by k and Q is n by k
        Frame ayqfrm = new Frame(dinfo._adaptedFrame);
        ayqfrm.add(ybig);
        for (int i = 0; i < _parms._nv; i++)
          ayqfrm.add("qcol_" + i, ayqfrm.anyVec().makeZero());
        Frame ayfrm = ayqfrm.subframe(0, ncolA + _parms._nv);   // [A,Y]
        Frame aqfrm = ayqfrm.subframe(0, ncolA);
        aqfrm.add(ayqfrm.subframe(ncolA + _parms._nv, ayqfrm.numCols()));   // [A,Q]
        yqfrm = ayqfrm.subframe(ncolA, ayqfrm.numCols());   // [Y,Q]
        xx = MemoryManager.malloc8d(_parms._nv, _parms._nv);
        LinearAlgebraUtils.computeQ(_job._key, yinfo, yqfrm, xx);

        model._output._iterations = 0;
        long qobs = dinfo._adaptedFrame.numRows() * _parms._nv;    // Number of observations in Q
        double qerr = 2 * TOLERANCE * qobs;   // Stop when average SSE between Q_j and Q_{j-2} below tolerance
        double average_SEE = qerr / qobs;

        int wEndCol = 2*_parms._nv-1;
        int wEndColR = _parms._nv-1;

        while ((model._output._iterations < 10 || average_SEE > TOLERANCE) && model._output._iterations < _parms._max_iterations) {   // Run at least 10 iterations before tolerance cutoff
          if(stop_requested()) break;
          _job.update(1, "Iteration " + String.valueOf(model._output._iterations+1) + " of randomized subspace iteration");

          // 2) Form \tilde{Y}_j = A'Q_{j-1} and compute \tilde{Y}_j = \tilde{Q}_j \tilde{R}_j factorization
          SMulTask stsk = new SMulTask(dinfo, _parms._nv, _ncolExp);
          stsk.doAll(aqfrm);    // Pass in [A,Q]

          if (_wideDataset) {
            if (model._output._iterations==0) {
              ysmallF = new water.util.ArrayUtils().frame(stsk._atq);
              ysmallInfo = new DataInfo(ysmallF, null, true, DataInfo.TransformType.NONE,
                      true, false, false);
              DKV.put(ysmallInfo._key, ysmallInfo);
              ysmall_q = MemoryManager.malloc8d(_ncolExp, _parms._nv);
              ysmallqfrm = new Frame(ysmallF);
              for (int i = 0; i < _parms._nv; i++)      // pray that _nv is small
                ysmallqfrm.add("qcol_" + i, ysmallqfrm.anyVec().makeZero());
            } else {  // replace content of ysmallqfrm with new contents in _atq,
              new CopyArrayToFrame(0, wEndColR, _ncolExp, stsk._atq).doAll(ysmallqfrm);
            }
            LinearAlgebraUtils.computeQ(_job._key, ysmallInfo, ysmallqfrm, xx);
            ysmall_q = new FrameToArray(_parms._nv, wEndCol, _ncolExp, ysmall_q).doAll(ysmallqfrm).getArray();

          } else { // let ysmall as 2-D double array
            Matrix ysmall = new Matrix(stsk._atq);  // small only for n_exp << m.  Not for wide dataset.
            QRDecomposition ysmall_qr = new QRDecomposition(ysmall);
            ysmall_q = ysmall_qr.getQ().getArray();  // memory allocation here too.
          }

          // 3) Form Y_j = A\tilde{Q}_j and compute Y_j = Q_jR_j factorization (ybig)
          BMulInPlaceTask tsk = new BMulInPlaceTask(dinfo, ArrayUtils.transpose(ysmall_q), _ncolExp);
          tsk.doAll(ayfrm);
          qerr = LinearAlgebraUtils.computeQ(_job._key, yinfo, yqfrm, xx);
          average_SEE = qerr/qobs;
          model._output._iterations++;

          // store variables for scoring history
          model._output._training_time_ms.add(System.currentTimeMillis());
          model._output._history_average_SEE.add(average_SEE);

          model.update(_job);
        }

        model._output._nobs = ybig.numRows(); // update nobs parameter
        model.update(_job);
        // 4) Extract and save final Q_j from [A,Q] frame
        qfrm = ayqfrm.extractFrame(ncolA + _parms._nv, ayqfrm.numCols());
        qfrm = new Frame(Key.<Frame>make(), qfrm.names(), qfrm.vecs());
        DKV.put(qfrm);
      } finally {
        if( yinfo != null ) yinfo.remove();
        if( ybig != null ) ybig.delete();
        if (ysmallInfo != null) ysmallInfo.remove();
        if (ysmallF != null) ysmallF.delete();
        if (ysmallqfrm != null) ysmallqfrm.delete();
      }
      return qfrm;
    }

    // Algorithm 5.1: Direct SVD from Halko et al (http://arxiv.org/pdf/0909.4061.pdf)
    private Frame directSVD(DataInfo dinfo, Frame qfrm, SVDModel model) {
      String u_name = (_parms._u_name == null || _parms._u_name.length() == 0) ? "SVDUMatrix_" + Key.rand() : _parms._u_name;
      return directSVD(dinfo, qfrm, model, u_name);
    }
    private Frame directSVD(DataInfo dinfo, Frame qfrm, SVDModel model, String u_name) {
      DataInfo qinfo = null;
      Frame u = null;
      final int ncolA = dinfo._adaptedFrame.numCols();


      try {
        Vec[] vecs = new Vec[ncolA + _parms._nv];
        for (int i = 0; i < ncolA; i++) vecs[i] = dinfo._adaptedFrame.vec(i);
        for (int i = 0; i < _parms._nv; i++) vecs[ncolA + i] = qfrm.vec(i);
        Frame aqfrm = new Frame(vecs);

        // 1) Form the matrix B' = A'Q = (Q'A)'
        _job.update(1, "Forming small matrix B = Q'A for direct SVD");
        SMulTask stsk = new SMulTask(dinfo, _parms._nv, _ncolExp);
        stsk.doAll(aqfrm);  // _atq size is _ncolExp by _nv

        if (_wideDataset) { // for wide dataset, calculate gram of B*T(B), get the SVD and proceed from there.
/*          double[][] xgram = ArrayUtils.formGram(stsk._atq, false);
          Matrix gramJ2 = new Matrix(xgram);  // form outer gram*/

          Frame tB = new water.util.ArrayUtils().frame(stsk._atq);
          DataInfo tbInfo = new DataInfo(tB, null, true, DataInfo.TransformType.NONE,
                  false, false, false);
          GramTask gtsk = new GramTask(_job._key, tbInfo).doAll(tB);
          Matrix gramJ = new Matrix(gtsk._gram.getXX());  // form outer gram
          SingularValueDecomposition svdJ = gramJ.svd();

            // 3) Form orthonormal matrix U = QV
          _job.update(1, "Forming distributed orthonormal matrix U");
          u=makeUVec(model, u_name, u, qfrm, new Matrix(stsk._atq), svdJ);
          model._output._d = ArrayUtils.mult((Arrays.copyOfRange(ArrayUtils.sqrtArr(svdJ.getSingularValues()),
                  0, _parms._nv)), sqrt(tB.numRows()));

          // to get v, we need to do T(A)*U*D^-1
          // stuff A and U into a frame
          Vec[] tvecs = new Vec[ncolA];
          for (int i = 0; i < ncolA; i++) tvecs[i] = dinfo._adaptedFrame.vec(i);
          Frame avfrm = new Frame(tvecs);
          Frame fromSVD = null;
          avfrm.add(u);
          model._output._v = (new SMulTask(dinfo, _parms._nv, _ncolExp).doAll(avfrm))._atq;

          // Perform T(A)*U and V is in _atq.  Need to be scaled by svd.
          model._output._v = ArrayUtils.mult(ArrayUtils.transpose(ArrayUtils.div(ArrayUtils.transpose(model._output._v),
                  model._output._d)), 1);

          if (fromSVD != null) fromSVD.delete();
          if (tB != null) tB.delete();
        } else {

          // 2) Compute SVD of small matrix: If B' = WDV', then B = VDW'
          _job.update(1, "Calculating SVD of small matrix locally");
          Matrix atqJ = new Matrix(stsk._atq);
          SingularValueDecomposition svdJ = atqJ.svd();

          // 3) Form orthonormal matrix U = QV
          _job.update(1, "Forming distributed orthonormal matrix U");

          if (_parms._keep_u) {
            u=makeUVec(model, u_name, u, qfrm, atqJ, svdJ);
          }

          model._output._d = Arrays.copyOfRange(svdJ.getSingularValues(), 0, _parms._nv);
          model._output._v = svdJ.getU().getMatrix(0, atqJ.getRowDimension() - 1, 0, _parms._nv - 1).getArray();
        }
      } finally {
        if( qinfo != null ) qinfo.remove();
      }
      return u;
    }

    /*
      Form orthonormal matrix U = QV
     */
    public Frame makeUVec(SVDModel model, String u_name, Frame u, Frame qfrm, Matrix atqJ, SingularValueDecomposition svdJ ) {
      model._output._u_key = Key.make(u_name);
      double[][] svdJ_u = svdJ.getV().getMatrix(0, atqJ.getColumnDimension() - 1, 0,
              _parms._nv - 1).getArray();
      DataInfo qinfo = new DataInfo(qfrm, null, true, DataInfo.TransformType.NONE,
              false, false, false);
      DKV.put(qinfo._key, qinfo);
      BMulTask btsk = new BMulTask(_job._key, qinfo, ArrayUtils.transpose(svdJ_u));
      btsk.doAll(_parms._nv, Vec.T_NUM, qinfo._adaptedFrame);
      qinfo.remove();
      return btsk.outputFrame(model._output._u_key, null, null);
    //  DKV.remove(qinfo._key);
    }
    @Override
    public void computeImpl() {
      SVDModel model = null;
      DataInfo dinfo = null, tinfo = null;
      Frame u = null, qfrm = null;
      Vec[] uvecs = null;

      try {
        init(true);   // Initialize parameters
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new SVDModel(dest(), _parms, new SVDModel.SVDOutput(SVD.this));
        model.delete_and_lock(_job);

        // store (possibly) rebalanced input train to pass it to nested SVD job
        Frame tranRebalanced = new Frame(_train);
        boolean frameHasNas = tranRebalanced.hasNAs();
        // 0) Transform training data and save standardization vectors for use in scoring later
        if ((!_parms._impute_missing) && frameHasNas) { // remove NAs rows
          tinfo = new DataInfo(_train, _valid, 0, _parms._use_all_factor_levels, _parms._transform,
                  DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */
                  _parms._impute_missing, /* missingBucket */ false, /* weights */ false,
                    /* offset */ false, /* fold */ false, /* intercept */ false);
          DKV.put(tinfo._key, tinfo);

          DKV.put(tranRebalanced._key, tranRebalanced);
          _train = Rapids.exec(String.format("(na.omit %s)", tranRebalanced._key)).getFrame(); // remove NA rows
          DKV.remove(tranRebalanced._key);
          checkMemoryFootPrint();
        }
        dinfo = new DataInfo(_train, _valid, 0, _parms._use_all_factor_levels, _parms._transform,
                DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */
                _parms._impute_missing, /* missingBucket */ false, /* weights */ false,
                /* offset */ false, /* fold */ false, /* intercept */ false);
        DKV.put(dinfo._key, dinfo);

        if (!_parms._impute_missing && frameHasNas) {
          // fixed the std and mean of dinfo to that of the frame before removing NA rows
          dinfo._normMul = tinfo._normMul;
          dinfo._numMeans = tinfo._numMeans;
          dinfo._normSub = tinfo._normSub;
        }
        // Save adapted frame info for scoring later
        setSVDModel(model, dinfo);

        String u_name = (_parms._u_name == null || _parms._u_name.length() == 0) ? "SVDUMatrix_" + Key.rand() : _parms._u_name;
        String v_name = (_parms._v_name == null || _parms._v_name.length() == 0) ? "SVDVMatrix_" + Key.rand() : _parms._v_name;

        if(_parms._svd_method == SVDParameters.Method.GramSVD) {
          // Calculate and save Gram matrix of training data
          // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set (excluding rows with NAs)
          _job.update(1, "Begin distributed calculation of Gram matrix");
          GramTask gtsk = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
          Gram gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
          assert gram.fullN() == _ncolExp;
          model._output._nobs = gtsk._nobs;
          model._output._total_variance = gram.diagSum() * gtsk._nobs / (gtsk._nobs-1);  // Since gram = X'X/nobs, but variance requires nobs-1 in denominator
          model.update(_job);

          // Cannot calculate SVD if all rows contain missing value(s) and hence were skipped
          if(gtsk._nobs == 0)
            error("_train", "Every row in _train contains at least one missing value. Consider setting impute_missing = TRUE.");
          if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

          // Calculate SVD of G = A'A/n and back out SVD of A. If SVD of A = UDV' then A'A/n = V(D^2/n)V'
          _job.update(1, "Calculating SVD of Gram matrix locally");
          Matrix gramJ = new Matrix(gtsk._gram.getXX());
          SingularValueDecomposition svdJ = gramJ.svd();

          // Output diagonal of D
          _job.update(1, "Computing stats from SVD");
          double[] sval = svdJ.getSingularValues();
          model._output._d = MemoryManager.malloc8d(_parms._nv);
         // model._output._d = new double[_parms._nv];    // Only want rank = nv diagonal values
          for(int k = 0; k < _parms._nv; k++)
            model._output._d[k] = Math.sqrt(sval[k] * model._output._nobs);

          // Output right singular vectors V
          double[][] v = svdJ.getV().getArray();
          assert v.length == _ncolExp && LinearAlgebraUtils.numColsExp(dinfo._adaptedFrame,_parms._use_all_factor_levels) == _ncolExp;
          model._output._v = MemoryManager.malloc8d(_ncolExp, _parms._nv);
         // model._output._v = new double[_ncolExp][_parms._nv];  // Only want rank = nv decomposition
          for(int i = 0; i < v.length; i++)
            System.arraycopy(v[i], 0, model._output._v[i], 0, _parms._nv);

          // Calculate left singular vectors U = AVD^(-1) if requested
          if(_parms._keep_u) {
            model._output._u_key = Key.make(u_name);
            double[][] vt = ArrayUtils.transpose(model._output._v);
            for (int k = 0; k < _parms._nv; k++)
              ArrayUtils.div(vt[k], model._output._d[k]);
            BMulTask tsk = new BMulTask(_job._key, dinfo, vt).doAll(_parms._nv, Vec.T_NUM, dinfo._adaptedFrame);
            u = tsk.outputFrame(model._output._u_key, null, null);
          }
        } else if(_parms._svd_method == SVDParameters.Method.Power) {
          // Calculate and save Gram matrix of training data
          // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set (excluding rows with NAs)
          // NOTE: the Gram also will apply the specified Transforms on the data before performing the operation.
          // NOTE:  valid transforms are NONE, DEMEAN, STANDARDIZE...
          _job.update(1, "Begin distributed calculation of Gram matrix");
          GramTask gtsk = null;
          Gram.OuterGramTask ogtsk = null;
          Gram gram = null, gram_update=null;
          double[] randomInitialV = null; // store random initial eigenvectors, actually refering to V'
          double[] finalV = null;         // store eigenvectors obtained from powerLoop
          int eigVecLen = _ncolExp;       // size of one eigenvector
          GramUpdate guptsk = null;
          double[][] gramArrays = null;       // store outergram as a double array
          double[][] gramUpdatesW = null;     // store the result of (I-sum vi*T(vi))*A*T(A)*(I-sum vi*T(vi))

          //_estimatedSingularValues = new double[_parms._nv];  // allocate memory once
          _estimatedSingularValues = MemoryManager.malloc8d(_parms._nv);
          if (_wideDataset) {
            ogtsk = new Gram.OuterGramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
            gram = ogtsk._gram;
            model._output._nobs = ogtsk._nobs;
            eigVecLen = (int) gram.fullN();
          } else {
            gtsk = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
            gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
            assert gram.fullN() == _ncolExp;
            model._output._nobs = gtsk._nobs;
          }

          model._output._total_variance = gram.diagSum() * model._output._nobs / (model._output._nobs-1);  // Since gram = X'X/nobs, but variance requires nobs-1 in denominator
          model.update(_job);

          // 1) Run one iteration of power method
          _job.update(1, "Iteration 1 of power method");     // One unit of work
          // 1a) Initialize right singular vector v_1
          model._output._v = MemoryManager.malloc8d(_parms._nv, eigVecLen);
         // model._output._v = new double[_parms._nv][eigVecLen];  // Store V' for ease of use and transpose back at end
          randomInitialV = MemoryManager.malloc8d(eigVecLen);
         // randomInitialV = new double[eigVecLen];   // allocate memroy for randomInitialV and finalV once, save time
          finalV = MemoryManager.malloc8d(eigVecLen);

          //finalV = new double[eigVecLen];
          model._output._v[0] = Arrays.copyOf(powerLoop(gram, _parms._seed, model, randomInitialV, finalV, 0),
                  eigVecLen);

          // Keep track of I - \sum_i v_iv_i' where v_i = eigenvector i
          double[][] ivv_sum = new double[eigVecLen][eigVecLen];
          for (int i = 0; i < eigVecLen; i++) ivv_sum[i][i] = 1; //generate matrix I

          // 1b) Initialize singular value \sigma_1 and update u_1 <- Av_1
          if (!_parms._only_v) {
            model._output._d = new double[_parms._nv];  // allocate memory once

            if (!_wideDataset) {
              model._output._u_key = Key.make(u_name);
              uvecs = new Vec[_parms._nv];
              computeSigmaU(dinfo, model, 0, ivv_sum, uvecs, finalV);  // Compute first singular value \sigma_1
            }
          }
          model._output._iterations = 1;
          model.update(_job); // Update model in K/V store

          // 1c) Update Gram matrix A_1'A_1 = (I - v_1v_1')A'A(I - v_1v_1')
          updateIVVSum(ivv_sum, model._output._v[0]);
          // double[][] gram_update = ArrayUtils.multArrArr(ArrayUtils.multArrArr(ivv_sum, gram), ivv_sum);

          if (_wideDataset) {
            gramArrays = new double[eigVecLen][eigVecLen];    // memory allocation is done once here
            gramUpdatesW = new double[eigVecLen][eigVecLen];
            gram_update = new Gram(eigVecLen, 0, dinfo.numNums(), dinfo._cats,false);

            updateGram(ivv_sum, gramArrays, gramUpdatesW, gram, gram_update);
          } else {
            guptsk = new GramUpdate(_job._key, dinfo, ivv_sum).doAll(dinfo._adaptedFrame);
            gram_update = guptsk._gram;
          }

          for (int k = 1; k < _parms._nv; k++) {  // loop through for each eigenvalue/eigenvector...
            if (stop_requested()) break;
            if (_matrixRankReached) { // number of eigenvalues found is less than _nv
              int newk = k-1;
              _job.warn("_train SVD: Dataset is rank deficient.  _parms._nv was "+_parms._nv+" and is now set to "+newk);
              _parms._nv = newk;   // change number of eigenvector parameters to be the actual number of eigenvectors found
              break;
            }

            _job.update(1, "Iteration " + String.valueOf(k+1) + " of power method");   // One unit of work

            // 2) Iterate x_i <- (A_k'A_k/n)x_{i-1} until convergence and set v_k = x_i/||x_i||
            model._output._v[k] = Arrays.copyOf(powerLoop(gram_update, _parms._seed, model, randomInitialV, finalV,
                    k),
                    eigVecLen);

            // 3) Residual data A_k = A - \sum_{i=1}^k \sigma_i u_iv_i' = A - \sum_{i=1}^k Av_iv_i' = A(I - \sum_{i=1}^k v_iv_i')
            // 3a) Compute \sigma_k = ||A_{k-1}v_k|| and u_k = A_{k-1}v_k/\sigma_k
            if (!_parms._only_v && !_wideDataset)
              computeSigmaU(dinfo, model, k, ivv_sum, uvecs, finalV);

            // 3b) Compute Gram of residual A_k'A_k = (I - \sum_{i=1}^k v_jv_j')A'A(I - \sum_{i=1}^k v_jv_j')
            updateIVVSum(ivv_sum, model._output._v[k]);   // Update I - \sum_{i=1}^k v_iv_i' with sum up to current singular value
            // gram_update = ArrayUtils.multArrArr(ivv_sum, ArrayUtils.multArrArr(gram, ivv_sum));  // Too slow on wide arrays
            if (_wideDataset) {
              updateGram(ivv_sum, gramArrays, gramUpdatesW, gram, gram_update);
            } else {
              guptsk = new GramUpdate(_job._key, dinfo, ivv_sum).doAll(dinfo._adaptedFrame);
              gram_update = guptsk._gram;
            }
            model._output._iterations++;
            model.update(_job); // Update model in K/V store
          }     // end iteration to find eigenvectors

          if (!_parms._only_v && !_parms._keep_u && _wideDataset) {  // dealing with wide dataset per request from PCA, won't want U
            for (int vecIndex = 0; vecIndex < _parms._nv; vecIndex++) {
              model._output._d[vecIndex] = Math.sqrt(model._output._nobs*_estimatedSingularValues[vecIndex]);
            }
            model._output._v = transformEigenVectors(dinfo, transpose(model._output._v));
          }

          if (!_wideDataset) {
            // 4) Normalize output frame columns by singular values to get left singular vectors
            model._output._v = ArrayUtils.transpose(model._output._v);  // Transpose to get V (since vectors were stored as rows)
            if (!_parms._only_v && !_parms._keep_u) {         // Delete U vecs if computed, but user does not want it returned
              for (int index=0; index < _parms._nv; index++){
                uvecs[index].remove();
              }
              model._output._u_key = null;
            } else if (!_parms._only_v && _parms._keep_u) {   // Divide U cols by singular values and save to DKV
              u = new Frame(model._output._u_key, null, uvecs);
              DKV.put(u._key, u);
              DivideU utsk = new DivideU(model._output._d);
              utsk.doAll(u);
            }
          }

          if (_failedConvergence) {
            _job.warn("_train: PCA Power method failed to converge within TOLERANCE.  Increase max_iterations or " +
                    "reduce TOLERANCE to mitigate this problem.");
          }
          LinkedHashMap<String, ArrayList> scoreTable = new LinkedHashMap<String, ArrayList>();
          scoreTable.put("Timestamp", model._output._training_time_ms);
          scoreTable.put("err", model._output._history_err);
          scoreTable.put("Principal Component #", model._output._history_eigenVectorIndex);
          model._output._scoring_history = createScoringHistoryTableDR(scoreTable,
                  "Scoring History from Power SVD", _job.start_time());
        } else if(_parms._svd_method == SVDParameters.Method.Randomized) {
          qfrm = randSubIter(dinfo, model);
          u = directSVD(dinfo, qfrm, model, u_name);
          model._output._training_time_ms.add(System.currentTimeMillis());
          model._output._history_average_SEE.add(model._output._history_average_SEE.get(model._output._history_average_SEE.size()-1)); // add last err back to it
          LinkedHashMap<String, ArrayList> scoreTable = new LinkedHashMap<String, ArrayList>();
          scoreTable.put("Timestamp", model._output._training_time_ms);
          scoreTable.put("average SEE", model._output._history_average_SEE);
          model._output._scoring_history = createScoringHistoryTableDR(scoreTable,
                  "Scoring History from Randomized SVD", _job.start_time());
        } else
          error("_svd_method", "Unrecognized SVD method " + _parms._svd_method);

        if (_parms._save_v_frame) {
          model._output._v_key = Key.make(v_name);
          ArrayUtils.frame(model._output._v_key, null, model._output._v);
        }
        if (_matrixRankReached) { // need to shorten the correct eigen stuff
          model._output._d = Arrays.copyOf(model._output._d, _parms._nv);
          for (int index=0; index < model._output._v.length; index++) {
            model._output._v[index] = Arrays.copyOf(model._output._v[index],  _parms._nv);
          }
        }
        model._output._model_summary = createModelSummaryTable(model._output);
        model.update(_job);
      } catch (Throwable t) {
        t.printStackTrace();
        throw t;
      }
      finally {
        if( model != null ) model.unlock(_job);
        if( dinfo != null ) dinfo.remove();
        if (tinfo != null) tinfo.remove();
        if( u != null & !_parms._keep_u ) u.delete();
        if( qfrm != null ) qfrm.delete();

        List<Key<Vec>> keep = new ArrayList<>();
        if (model._output!=null) {
          if (model._output._u_key != null) {
            Frame uFrm = DKV.getGet(model._output._u_key);
            if (uFrm != null) for (Vec vec : uFrm.vecs()) keep.add(vec._key);
          }
          Frame vFrm = DKV.getGet(model._output._v_key);
          if (vFrm != null) for (Vec vec : vFrm.vecs()) keep.add(vec._key);
        }
        Scope.untrack(keep);
      }
    }
  }

  /*
  This method will calculate (I-v1*T(v1))*A*T(A)*(I-v1*T(v1)).  Note that we already have
  A*T(A) part as a gram matrix.  The ivv_sum part provides the (I-v1*T(v1)).  All we need to
  do here is to get the product and put it into a brand new gram matrix.
   */
  private void updateGram(double[][] ivv_sum, double[][] gramToArray, double[][] resultGram, Gram gram, Gram gramUpdate)
  {
    int numRows = gram.fullN();

    // grab gram matrix (A*T(A)) and expand into full matrix represented as 2D double array.
    for (int row_index=0; row_index < numRows; row_index++) {
      for (int col_index=0; col_index < numRows; col_index++) {
        if (col_index <= row_index) {
          gramToArray[row_index][col_index] = gram._xx[row_index][col_index];
        } else {
          gramToArray[row_index][col_index] = gram._xx[col_index][row_index];
        }
      }
    }

    resultGram = multArrArr(ivv_sum, gramToArray);    // resultGram = (I-v1*T(v1))*A*T(A)
    gramToArray = multArrArr(resultGram, ivv_sum);    // overwrite gramToArray with final result resultGram*(I-v1*T(v1))

    // copy over results from matrix multiplication output to resultGram
    for (int row_index = 0; row_index < numRows; row_index++) {
      for (int col_index = 0; col_index <= row_index; col_index++) {
        gramUpdate._xx[row_index][col_index] = gramToArray[row_index][col_index];
      }
    }
  }

  /*
  This method may make changes to the dinfo parameters if SVD is called by GLRM as a init method.
   */
  private void setSVDModel(SVDModel model, DataInfo dinfo) {
    if (_callFromGLRM) {
      dinfo._normSub = Arrays.copyOf(_glrmModel._output._normSub, _glrmModel._output._normSub.length);
      dinfo._normMul = Arrays.copyOf(_glrmModel._output._normMul, _glrmModel._output._normMul.length);
      dinfo._permutation = Arrays.copyOf(_glrmModel._output._permutation, _glrmModel._output._permutation.length);
      dinfo._numMeans = Arrays.copyOf(dinfo._normSub, dinfo._normSub.length);
      dinfo._nums = _glrmModel._output._nnums;
      dinfo._cats = _glrmModel._output._ncats;
      dinfo._catOffsets = Arrays.copyOf(_glrmModel._output._catOffsets, _glrmModel._output._catOffsets.length);
      model._output._names_expanded = Arrays.copyOf(_glrmModel._output._names_expanded,
              _glrmModel._output._names_expanded.length);
    } else
      model._output._names_expanded = dinfo.coefNames();

    model._output._normSub = dinfo._normSub == null ? new double[dinfo._nums] : dinfo._normSub;
    if (dinfo._normMul == null) {
      model._output._normMul = new double[dinfo._nums];
      Arrays.fill(model._output._normMul, 1.0);
    } else
      model._output._normMul = dinfo._normMul;
    model._output._permutation = dinfo._permutation;
    model._output._nnums = dinfo._nums;
    model._output._ncats = dinfo._cats;
    model._output._catOffsets = dinfo._catOffsets;
  }

  private TwoDimTable createModelSummaryTable(SVDModel.SVDOutput output) {
    if(null == output._d) return null;

    String[] colTypes = new String[_parms._nv];
    String[] colFormats = new String[_parms._nv];
    String[] colHeaders = new String[_parms._nv];
    Arrays.fill(colTypes, "double");
    Arrays.fill(colFormats, "%5f");
    for(int i = 0; i < colHeaders.length; i++) colHeaders[i] = "sval" + String.valueOf(i + 1);
    return new TwoDimTable("Singular values", null, new String[1],
            colHeaders, colTypes, colFormats, "", new String[1][],
            new double[][]{output._d});
  }

  private static class CalcSigmaU extends FrameTask<CalcSigmaU> {
    final double[] _svec;
    public double _sval;
    public long _nobs;

    public CalcSigmaU(Key<Job> jobKey, DataInfo dinfo, double[] svec) {
      super(jobKey, dinfo);
      _svec = svec;
      _sval = 0;
    }

    @Override protected void processRow(long gid, DataInfo.Row r, NewChunk[] outputs) {
      double num = r.innerProduct(_svec);
      outputs[0].addNum(num);
      _sval += num * num;
      ++_nobs;
    }

    @Override public void reduce(CalcSigmaU other) {
      _nobs += other._nobs;
      _sval += other._sval;
    }

    @Override protected void postGlobal() {
      _sval = Math.sqrt(_sval);
    }
  }

  private static class GramUpdate extends FrameTask<GramUpdate> {
    final double[][] _ivv;
    public Gram _gram;
    public long _nobs;

    public GramUpdate(Key<Job> jobKey, DataInfo dinfo, double[][] ivv) {
      super(jobKey, dinfo);
      assert null != ivv && ivv.length == ivv[0].length;
      _ivv = ivv;
    }

    @Override protected boolean chunkInit(){
      // To avoid memory allocation during every iteration.
      _gram = new Gram(_dinfo.fullN(), 0, _ivv.length, 0, false);
      _numRow = _dinfo.newDenseRow(MemoryManager.malloc8d(_ivv.length),0);
      return true;
    }

    private transient Row _numRow;
    @Override protected void processRow(long gid, DataInfo.Row r) {
      double w = 1; // TODO: add weights to dinfo?
      double[] nums = _numRow.numVals;
      for(int row = 0; row < _ivv.length; row++)
        nums[row] = r.innerProduct(_ivv[row]);
      _gram.addRow(_numRow, w);
      ++_nobs;
    }

    @Override protected void chunkDone(long n){
      double r = 1.0/_nobs;
      _gram.mul(r);
    }

    @Override public void reduce(GramUpdate gt){
      double r1 = (double)_nobs/(_nobs+gt._nobs);
      _gram.mul(r1);
      double r2 = (double)gt._nobs/(_nobs+gt._nobs);
      gt._gram.mul(r2);
      _gram.add(gt._gram);
      _nobs += gt._nobs;
    }
  }

  private static class DivideU extends MRTask<DivideU> {
    final double[] _sigma;

    public DivideU(double[] sigma) {
      _sigma = sigma;
    }

    @Override public void map(Chunk cs[]) {
      assert _sigma.length == cs.length;

      for (int col = 0; col < cs.length; col++) {
        for(int row = 0; row < cs[0].len(); row++) {
          double x = cs[col].atd(row);
          cs[col].set(row, x / _sigma[col]);
        }
      }
    }
  }

  // Compute Y = AG where A is n by p and G is a p by k standard Gaussian matrix
  private static class RandSubInit extends FrameTask<RandSubInit> {
    final double[][] _gaus;   // G' is k by p for convenient multiplication

    public RandSubInit(Key<Job> jobKey, DataInfo dinfo, double[][] gaus) {
      super(jobKey, dinfo);
      _gaus = gaus;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
      for(int k = 0; k < _gaus.length; k++) {
        double y = row.innerProduct(_gaus[k]);
        outputs[k].addNum(y);
      }
    }
  }
}
