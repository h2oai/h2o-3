package hex.glm;

import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import java.util.Arrays;
import jsr166y.CountedCompleter;
import water.H2O;
import water.Iced;
import water.Key;
import water.MemoryManager;



/**
 * Distributed least squares solvers
 * @author tomasnykodym
 *
 */
public abstract class LSMSolver extends Iced{

  public enum LSMSolverType {
    AUTO, // AUTO: (len(beta) < 1000)?ADMM:GenGradient
    ADMM,
    GenGradient
  }
  double _lambda;
  final double _alpha;
  public Key _jobKey;
  public String _id;

  public LSMSolver(double lambda, double alpha){
    _lambda = lambda;
    _alpha  = alpha;
  }

  public final double [] grad(Gram gram, double [] beta, double [] xy){
    double [] grad = gram.mul(beta);
    for(int i = 0; i < grad.length; ++i)
      grad[i] -= xy[i];
    return grad;
  }


  public static void subgrad(final double alpha, final double lambda, final double [] beta, final double [] grad){
    if(beta == null)return;
    final double l1pen = lambda*alpha;
    for(int i = 0; i < grad.length-1; ++i) {// add l2 reg. term to the gradient
      if(beta[i] < 0) grad[i] -= l1pen;
      else if(beta[i] > 0) grad[i] += l1pen;
      else grad[i] = LSMSolver.shrinkage(grad[i], l1pen);
    }
  }

  /**
   *  @param xy - guassian: -X'y binomial: -(1/4)X'(XB + (y-p)/(p*1-p))
   *  @param yy - &lt; y,y &gt; /2
   *  @param newBeta - resulting vector of coefficients
   *  @return true if converged
   *
   */
  public abstract boolean solve(Gram gram, double [] xy, double yy, double [] newBeta);

  protected boolean _converged;

  public final boolean converged(){return _converged;}
  public static class LSMSolverException extends RuntimeException {
    public LSMSolverException(String msg){super(msg);}
  }
  public abstract String name();


  protected static double shrinkage(double x, double kappa) {
    double sign = x < 0?-1:1;
    double sx = x*sign;
    if(sx <= kappa) return 0;
    return sign*(sx - kappa);
//    return Math.max(0, x - kappa) - Math.max(0, -x - kappa);
  }

  /**
   * Compute least squares objective function value:
   *    lsm_obj(beta) = 0.5*(y - X*b)'*(y - X*b) + l1 + l2
   *                  = 0.5*y'y - (X'y)'*b + 0.5*b'*X'X*b) + l1 + l2
   *    l1 = alpha*lambda_value*l1norm(beta)
   *    l2 = (1-alpha)*lambda_value*l2norm(beta)/2
   * @param xy:   X'y
   * @param yy:   0.5*y'y
   * @param beta: b (vector of coefficients)
   * @param xb: X'X*beta
   * @return 0.5*(y - X*b)'*(y - X*b) + l1 + l2
   */
  protected double objectiveVal(double[] xy, double yy, double[] beta, double [] xb) {
    double res = lsm_objectiveVal(xy,yy,beta, xb);
    double l1 = 0, l2 = 0;
    for(int i = 0; i < beta.length; ++i){
      l1 += Math.abs(beta[i]);
      l2 += beta[i]*beta[i];
    }
    return res + _alpha*_lambda*l1 + 0.5*(1-_alpha)*_lambda*l2;
  }

  /**
   * Compute the LSM objective.
   *
   *   lsm_obj(beta) = 0.5 * (y - X*b)' * (y - X*b)
   *                 = 0.5 * y'y - (X'y)'*b + 0.5*b'*X'X*b)
   *                 = 0.5yy + b*(0.5*X'X*b - X'y)
   * @param xy X'y
   * @param yy y'y
   * @param beta
   * @param xb X'X*beta
   * @return
   */
  protected double lsm_objectiveVal(double[] xy, double yy, double[] beta, double [] xb) {
    double res = 0.5*yy;
    for(int i = 0; i < xb.length; ++i)
      res += beta[i]*(0.5*xb[i] - xy[i]);
    return res;
  }

  static final double[] mul(double[][] X, double[] y, double[] z) {
    final int M = X.length;
    final int N = y.length;
    for( int i = 0; i < M; ++i ) {
      z[i] = X[i][0] * y[0];
      for( int j = 1; j < N; ++j )
        z[i] += X[i][j] * y[j];
    }
    return z;
  }

  static final double[] mul(double[] x, double a, double[] z) {
    for( int i = 0; i < x.length; ++i )
      z[i] = a * x[i];
    return z;
  }

  static final double[] plus(double[] x, double[] y, double[] z) {
    for( int i = 0; i < x.length; ++i )
      z[i] = x[i] + y[i];
    return z;
  }

  static final double[] minus(double[] x, double[] y, double[] z) {
    for( int i = 0; i < x.length; ++i )
      z[i] = x[i] - y[i];
    return z;
  }

  static final double[] shrink(double[] x, double[] z, double kappa) {
    for( int i = 0; i < x.length - 1; ++i )
      z[i] = shrinkage(x[i], kappa);
    z[x.length - 1] = x[x.length - 1]; // do not penalize intercept!
    return z;
  }



  public static final class ADMMSolver extends LSMSolver {
    //public static final double DEFAULT_LAMBDA = 1e-5;
    public static final double DEFAULT_ALPHA = 0.5;
    public double [] _wgiven;
    public double _proximalPenalty;
    final public double _gradientEps;
    private static final double GLM1_RHO = 1.0e-3;

    public double gerr = Double.POSITIVE_INFINITY;
    public int iterations = 0;
    public long decompTime;
    public boolean normalize() {return _lambda != 0;}

    public double _addedL2;
    public ADMMSolver (double lambda, double alpha, double gradEps) {
      super(lambda,alpha);
      _gradientEps = gradEps;
    }
    public ADMMSolver (double lambda, double alpha, double gradEps,double addedL2) {
      super(lambda,alpha);
      _addedL2 = addedL2;
      _gradientEps = gradEps;
    }

    public static class NonSPDMatrixException extends LSMSolverException {
      public NonSPDMatrixException(){super("Matrix is not SPD, can't solve without regularization\n");}
      public NonSPDMatrixException(Gram grm){

        super("Matrix is not SPD, can't solve without regularization\n" + grm);
      }
    }

    @Override
    public boolean solve(Gram gram, double [] xy, double yy, double[] z) {
      return solve(gram, xy, yy, z, Double.POSITIVE_INFINITY);
    }

    private static double l1_norm(double [] v){
      double res = 0;
      for(double d:v)res += Math.abs(d);
      return res;
    }
    private static double l2_norm(double [] v){
      double res = 0;
      for(double d:v)res += d*d;
      return res;
    }

    private double converged(Gram g, double [] beta, double [] xy){
      double [] grad = grad(g,beta,xy);
      subgrad(_alpha,_lambda,beta,grad);
      double err = 0;
      for(double d:grad)
        if(d > err)err = d;
        else if(d < -err)err = -d;
      return err;
    }


    private double getGrad(Gram gram, double [] beta, double [] xy){
      double [] g = grad(gram,beta,xy);
      double err = 0;
      for(double d3:g)
        if(d3 > err)err = d3;
        else if(d3 < -err)err = -d3;
      return err;
    }

    public ParallelSolver parSolver(Gram gram, double [] xy, double [] res, double rho, int iBlock, int rBlock){
      return new ParallelSolver(gram, xy, res, rho,iBlock, rBlock);
    }
    public final class ParallelSolver extends H2O.H2OCountedCompleter {
      final Gram gram;
      final double rho;
      final double kappa;
      double _bestErr = Double.POSITIVE_INFINITY;
      double _lastErr = Double.POSITIVE_INFINITY;
      final double [] xy;
      double [] _xyPrime;
      double _orlx;
      int _k;
      final double [] u;
      final double [] z;
      Cholesky chol;
      final double d;
      int _iter;

      final int N;
      final int max_iter;
      final int round;
      final int _iBlock;
      final int _rBlock;

      private ParallelSolver(Gram g, double [] xy, double [] res, double rho, int iBlock, int rBlock){
        _iBlock = iBlock;
        _rBlock = rBlock;
        gram = g; this.xy = xy; this.z = res;;
        N = xy.length;
        d = gram._diagAdded;
        this.rho = rho;
        u = MemoryManager.malloc8d(N);
        kappa = _lambda*_alpha/rho;
        max_iter = (int)(10000*(250.0/(1+xy.length)));
        round = Math.max(20,(int)(max_iter*0.01));
        _k = round;
      }
      @Override
      public void compute2() {
        Arrays.fill(z, 0);
        if(_lambda>0 || _addedL2 > 0)
          gram.addDiag(_lambda*(1-_alpha) + _addedL2);
        if(_alpha > 0 && _lambda > 0)
          gram.addDiag(rho);
        if(_proximalPenalty > 0 && _wgiven != null){
          gram.addDiag(_proximalPenalty, true);
          for(int i = 0; i < xy.length; ++i)
            xy[i] += _proximalPenalty*_wgiven[i];
        }
        int attempts = 0;
        long t1 = System.currentTimeMillis();
        chol = gram.cholesky(null,true,_id);
        long t2 = System.currentTimeMillis();
        while(!chol.isSPD() && attempts < 10){
          if(_addedL2 == 0) _addedL2 = 1e-5;
          else _addedL2 *= 10;
          ++attempts;
          gram.addDiag(_addedL2); // try to add L2 penalty to make the Gram issp
          gram.cholesky(chol);
        }
        decompTime = (t2-t1);
        if(!chol.isSPD())
          throw new NonSPDMatrixException(gram);
        if(_alpha == 0 || _lambda == 0){ // no l1 penalty
          System.arraycopy(xy, 0, z, 0, xy.length);
          chol.parSolver(this,z,_iBlock,_rBlock).fork();
          return;
        }
        gerr = Double.POSITIVE_INFINITY;
        _xyPrime = xy.clone();
        _orlx = 1.8; // over-relaxation
        // first compute the x update
        // add rho*(z-u) to A'*y
        new ADMMIteration(this).fork();
      }
      @Override public void onCompletion(CountedCompleter caller){
        gram.addDiag(-gram._diagAdded + d);
        assert gram._diagAdded == d;
      }
      private final class ADMMIteration extends CountedCompleter {
        final long t1;
        public ADMMIteration(H2O.H2OCountedCompleter cmp){super(cmp); t1 = System.currentTimeMillis();}

        @Override public void compute(){
          ++_iter;
          final double [] xyPrime = _xyPrime;
          // first compute the x update
          // add rho*(z-u) to A'*y
          for( int j = 0; j < N-1; ++j )xyPrime[j] = xy[j] + rho*(z[j] - u[j]);
          xyPrime[N-1] = xy[N-1];
          // updated x
          chol.parSolver(this,xyPrime,_iBlock,_rBlock).fork();
        }
        @Override
        public void onCompletion(CountedCompleter caller) {
          final double [] xyPrime = _xyPrime;
          final double orlx = _orlx;
          // compute u and z updateADMM
          for( int j = 0; j < N-1; ++j ) {
            double x_hat = xyPrime[j];
            x_hat = x_hat * orlx + (1 - orlx) * z[j];
            z[j] = shrinkage(x_hat + u[j], kappa);
            u[j] += x_hat - z[j];
          }
          z[N-1] = xyPrime[N-1];
          if(_iter == _k) {
            double[] grad = grad(gram, z, xy);
            subgrad(_alpha, _lambda, z, grad);
            for (int x = 0; x < grad.length - 1; ++x) {
              if (gerr < grad[x] || gerr < -grad[x])
                gerr = grad[x];
            }
            if (gerr < 9e-4)
              return;

//          if(grad < bestErr){
//            bestErr = err;
//            System.arraycopy(z,0,res,0,z.length);
//            if(err < _gradientEps)
//              break;
//          } else {
//            boolean allzeros = true;
//            for (int x = 0; allzeros && x < z.length - 1; ++x)
//              allzeros = z[x] == 0;
//            if (!allzeros) { // only want this check if we're past the warm up period (there can be many iterations with all zeros!)
//              // did not converge, check if we can converge in reasonable time
//              if (diff < 1e-4)  // we won't ever converge with this setup (maybe change rho and try again?)
//                break;
//              orlx = (1 + 15 * orlx) * 0.0625;
//            } else
//              orlx = 1.8;
//          }
//          lastErr = err;
            _k += round;
          }
          if(_iter < max_iter){
            getCompleter().addToPendingCount(1);
            new ADMMIteration((H2O.H2OCountedCompleter)getCompleter()).fork();
          }
        }
      }
    }
    final static double RELTOL = 1e-4;
    public boolean solve(Gram gram, double [] xy, double yy, final double[] z, final double rho) {
      gerr = 0;
      double d = gram._diagAdded;
      final int N = xy.length;
      Arrays.fill(z, 0);
      if(_lambda>0 || _addedL2 > 0)
        gram.addDiag(_lambda*(1-_alpha) + _addedL2);
      if(_alpha > 0 && _lambda > 0)
        gram.addDiag(rho);
      if(_proximalPenalty > 0 && _wgiven != null){
        gram.addDiag(_proximalPenalty, true);
        xy = xy.clone();
        for(int i = 0; i < xy.length; ++i)
          xy[i] += _proximalPenalty*_wgiven[i];
      }
      int attempts = 0;
      long t1 = System.currentTimeMillis();
      Cholesky chol = gram.cholesky(null,true,_id);
      long t2 = System.currentTimeMillis();
      while(!chol.isSPD() && attempts < 10){
        if(_addedL2 == 0) _addedL2 = 1e-5;
        else _addedL2 *= 10;
        ++attempts;
        gram.addDiag(_addedL2); // try to add L2 penalty to make the Gram issp
        gram.cholesky(chol);
      }
      decompTime = (t2-t1);
      if(!chol.isSPD())
        throw new NonSPDMatrixException(gram);
      if(_alpha == 0 || _lambda == 0){ // no l1 penalty
        System.arraycopy(xy, 0, z, 0, xy.length);
        chol.solve(z);
        gram.addDiag(-gram._diagAdded + d);
        return true;
      }
      double[] u = MemoryManager.malloc8d(N);
      double [] xyPrime = xy.clone();
      double kappa = _lambda*_alpha/rho;
      int i;
      int max_iter = Math.max(500,(int)(50000.0/(1+(xy.length >> 3))));
      double orlx = 1.8; // over-relaxation
      double reltol = RELTOL;
      for(i = 0; i < max_iter; ++i ) {
        long tX = System.currentTimeMillis();
        // first compute the x update
        // add rho*(z-u) to A'*y
        for( int j = 0; j < N-1; ++j )
          xyPrime[j] = xy[j] + rho*(z[j] - u[j]);
        xyPrime[N-1] = xy[N-1];
        // updated x
        chol.solve(xyPrime);
        // compute u and z updateADMM
        double rnorm = 0, snorm = 0, unorm = 0, xnorm = 0;
        for( int j = 0; j < N-1; ++j ) {
          double x = xyPrime[j];
          double zold = z[j];
          double x_hat = x * orlx + (1 - orlx) * zold;
          z[j] = shrinkage(x_hat + u[j], kappa);
          u[j] += x_hat - z[j];
          double r = xyPrime[j] - z[j];
          double s = z[j] - zold;
          rnorm += r*r;
          snorm += s*s;
          xnorm += x*x;
          unorm += u[j]*u[j];
        }
        z[N-1] = xyPrime[N-1];
        if(rnorm < reltol*xnorm && snorm < reltol*unorm){
          gerr = 0;
          double [] grad = grad(gram,z,xy);
          subgrad(_alpha,_lambda,z,grad);
          for(int x = 0; x < grad.length-1; ++x){
            if(gerr < grad[x]) gerr = grad[x];
            else if(gerr < -grad[x]) gerr = -grad[x];
          }
          if(gerr < 1e-4 || reltol <= 1e-6)break;
          while(rnorm < reltol*xnorm && snorm < reltol*unorm)
            reltol *= .1;
        }
        if(i % 20 == 0)
          orlx = (1 + 15 * orlx) * 0.0625;
      }
      gram.addDiag(-gram._diagAdded + d);
      assert gram._diagAdded == d;
      iterations = i;
      return _converged = (gerr < _gradientEps);
    }
    @Override
    public String name() {return "ADMM";}
  }

  public static final class ProxSolver extends LSMSolver {
    public ProxSolver(double lambda, double alpha){super(lambda,alpha);}

    /**
     * @param newB
     * @param oldObj
     * @param oldB
     * @param
     * @param t
     * @return
     */
    private static final double f_hat(double [] newB,double oldObj, double [] oldB,double [] xb, double [] xy, double t){
      double res = oldObj;
      double l2 = 0;
      for(int i = 0; i < newB.length; ++i){
        double diff = newB[i] - oldB[i];
        res += (xb[i]-xy[i])*diff;
        l2 += diff*diff;
      }
      return res + 0.25*l2/t;
    }
    private double penalty(double [] beta){
      double l1 = 0,l2 = 0;
      for(int i = 0; i < beta.length; ++i){
        l1 += Math.abs(beta[i]);
        l2 += beta[i]*beta[i];
      }
      return _lambda*(_alpha*l1 + (1-_alpha)*l2*0.5);
    }
    private static double betaDiff(double [] b1, double [] b2){
      double res = 0;
      for(int i = 0; i < b1.length; ++i)
        Math.max(res, Math.abs(b1[i] - b2[i]));
      return res;
    }
    @Override
    public boolean solve(Gram gram, double [] xy, double yy, double[] beta) {
      ADMMSolver admm = new ADMMSolver(_lambda,_alpha,1e-2);
      if(gram != null)return admm.solve(gram,xy,yy,beta);
      Arrays.fill(beta,0);
      long t1 = System.currentTimeMillis();
      final double [] xb = gram.mul(beta);
      double objval = objectiveVal(xy,yy,beta,xb);
      final double [] newB = MemoryManager.malloc8d(beta.length);
      final double [] newG = MemoryManager.malloc8d(beta.length);
      double step = 1;
      final double l1pen = _lambda*_alpha;
      final double l2pen = _lambda*(1-_alpha);
      double lsmobjval = lsm_objectiveVal(xy,yy,beta,xb);
      boolean converged = false;
      final int intercept = beta.length-1;
      int iter = 0;
      MAIN:
      while(!converged && iter < 1000) {
        ++iter;
        step = 1;
        while(step > 1e-12){ // line search
          double l2shrink = 1/(1+step*l2pen);
          double l1shrink = l1pen*step;
          for(int i = 0; i < beta.length-1; ++i)
            newB[i] = l2shrink*shrinkage((beta[i]-step*(xb[i]-xy[i])),l1shrink);
          newB[intercept] = beta[intercept] - step*(xb[intercept]-xy[intercept]);
          gram.mul(newB, newG);
          double newlsmobj = lsm_objectiveVal(xy, yy, newB,newG);
          double fhat = f_hat(newB,lsmobjval,beta,xb,xy,step);
          if(newlsmobj <= fhat){
            lsmobjval = newlsmobj;
            converged = betaDiff(beta,newB) < 1e-6;
            System.arraycopy(newB,0,beta,0,newB.length);
            System.arraycopy(newG,0,xb,0,newG.length);
            continue MAIN;
          } else step *= 0.8;
        }
        converged = true;
      }
      return converged;
    }
    public String name(){return "ProximalGradientSolver";}
  }
}
