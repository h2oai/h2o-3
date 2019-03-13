package hex.optimization;

import water.H2O;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;
import water.util.MathUtils.Norm;
import java.util.Arrays;

/**
 * Created by tomasnykodym on 3/2/15.
 */
public class ADMM {

  public interface ProximalSolver {
    public double []  rho();
    public boolean solve(double [] beta_given, double [] result);
    public boolean hasGradient();
    public OptimizationUtils.GradientInfo gradient(double [] beta);
    public int iter();
  }

  public static class L1Solver {
    final double RELTOL;
    final double ABSTOL;
    double gerr;
    int iter;
    final double _eps;
    final int max_iter;
    int _nclass = 1;  // default is one
    int[][] _activeColsAll;

    MathUtils.Norm _gradientNorm = Norm.L_Infinite;

    public double [] _u;

    public static double DEFAULT_RELTOL = 1e-2;
    public static double DEFAULT_ABSTOL = 1e-4;
    public L1Solver setGradientNorm(MathUtils.Norm n) {_gradientNorm = n; return this;}
    public L1Solver(double eps, int max_iter, double [] u) {
      this(eps,max_iter,DEFAULT_RELTOL,DEFAULT_ABSTOL,u);
    }

    public L1Solver(double eps, int max_iter, double [] u, int nclass, int[][] activeCols) {
      this(eps,max_iter,DEFAULT_RELTOL,DEFAULT_ABSTOL,u,nclass, activeCols);
    }

    public L1Solver(double eps, int max_iter, double reltol, double abstol, double [] u) {
      _eps = eps; this.max_iter = max_iter;
      _u = u;
      RELTOL = reltol;
      ABSTOL = abstol;
    }
    
    public L1Solver(double eps, int max_iter, double reltol, double abstol, double [] u, int nclass, int[][] activeCols) {
      this(eps, max_iter, reltol, abstol, u);
      _nclass=nclass;
      _activeColsAll = activeCols;
    }

    public L_BFGS.ProgressMonitor _pm;
    public boolean solve(ProximalSolver solver, double[] res, double lambda, boolean hasIntercept) {
      return solve(solver, res, lambda, hasIntercept, null, null);
    }

    private double computeErr(double[] z, double[] grad, double lambda, double[] lb, double[] ub) {
      grad = grad.clone();
      // check the gradient
      gerr = 0;
      if (lb != null)
        for (int j = 0; j < z.length; ++j)
          if (z[j] == lb[j] && grad[j] > 0)
            grad[j] = z[j] >= 0?-lambda:lambda;
      if (ub != null)
        for (int j = 0; j < z.length; ++j)
          if (z[j] == ub[j] && grad[j] < 0)
            grad[j] = z[j] >= 0?-lambda:lambda;
      subgrad(lambda, z, grad);
      switch(_gradientNorm) {
        case L_Infinite:
          gerr = ArrayUtils.linfnorm(grad,false);
          break;
        case L2_2:
          gerr = ArrayUtils.l2norm2(grad, false);
          break;
        case L2:
          gerr = Math.sqrt(ArrayUtils.l2norm2(grad, false));
          break;
        case L1:
          gerr = ArrayUtils.l1norm(grad,false);
          break;
        default:
          throw H2O.unimpl();
      }
      return gerr;
    }

    /**
     * 
     * @param solver
     * @param z: store xy at the beginning.  Will end up storing new beta at the end
     * @param l1pen
     * @param hasIntercept
     * @param lb
     * @param ub
     * @return
     */
    public boolean solve(ProximalSolver solver, double[] z, double l1pen, boolean hasIntercept, double[] lb, 
                         double[] ub) {
      gerr = Double.POSITIVE_INFINITY;
      iter = 0;
      if (l1pen == 0 && lb == null && ub == null) {
        solver.solve(null, z);
        return true;
      }
      int hasIcpt = hasIntercept?1:0;
      int N = z.length;
      double abstol = ABSTOL * Math.sqrt(N);
      double [] rho = solver.rho();
      double [] x = z.clone();
      Arrays.fill(z, 0.0);  //
      double [] beta_given = MemoryManager.malloc8d(N);
      double [] u;
      int coeffPClass = _activeColsAll==null?z.length/_nclass:_activeColsAll[0][_activeColsAll[0].length-1]+1;
      boolean useAllPreds = _activeColsAll==null;
      if(_u != null) {
        u = _u;
        int offset = 0;
        for (int classInd=0; classInd < _nclass; classInd++) {
          int lastIndex = useAllPreds?coeffPClass-hasIcpt:_activeColsAll[classInd].length-hasIcpt;
          for (int i = 0; i < lastIndex; ++i) { // intercept terms are left alone
            int trueInd = i + offset;
            beta_given[trueInd] = z[trueInd] - _u[trueInd];
          }
          offset += useAllPreds?coeffPClass:_activeColsAll[classInd].length;

        }
      } else u = _u = MemoryManager.malloc8d(z.length);
      double [] kappa = MemoryManager.malloc8d(rho.length);

      if (l1pen > 0) {
        int offset = 0;
        for (int classInd = 0; classInd < _nclass; classInd++) {  // skip intercepts here, prepare for soft thresholding
          int lastIndex = useAllPreds?coeffPClass-hasIcpt:_activeColsAll[classInd].length - hasIcpt;
          for (int i=0; i<lastIndex; i++) {
            int cind = i+offset;
            kappa[cind] = rho[cind]!=0?l1pen/rho[cind]:0;
          }
          offset+=useAllPreds?coeffPClass:_activeColsAll[classInd].length;
        }
      }
      int i;
      double orlx = 1.0; // over-relaxation
      double reltol = RELTOL;
      for (i = 0; i < max_iter && solver.solve(beta_given, x); ++i) { // beta_given is z-u, x = xy at first, store xk
        if(_pm != null && (i + 1) % 5 == 0)_pm.progress(z,solver.gradient(z));  // z = xy at the beginning
        // compute u and z updateADMM
        double rnorm = 0, snorm = 0, unorm = 0, xnorm = 0;
        int offset = 0;
        for (int classInd = 0; classInd < _nclass; classInd++) {
          int lastIndex = useAllPreds?coeffPClass - hasIcpt:_activeColsAll[classInd].length-hasIcpt;
          for (int j = 0; j < lastIndex; ++j) { // intercepts are excluded here
            int trueInd = j+offset;
            double xj = x[trueInd]; // contains the solution of inv(Gram)*(_xy+rho(z-u))
            double zjold = z[trueInd];
            double x_hat = xj * orlx + (1 - orlx) * zjold;  // average new x from solve with old z value
            double zj = shrinkage(x_hat + u[trueInd], kappa[trueInd]);  // apply soft thresholding as on page 43
            if (lb != null && zj < lb[trueInd])
              zj = lb[trueInd];
            if (ub != null && zj > ub[trueInd])
              zj = ub[trueInd];
            u[trueInd] += x_hat - zj; // update on page 43 for u
            beta_given[trueInd] = zj - u[trueInd];
            double r = xj - zj;
            double s = zj - zjold;
            rnorm += r * r;
            snorm += s * s;
            xnorm += xj * xj;
            unorm += rho[trueInd] * rho[trueInd] * u[trueInd] * u[trueInd];
            z[trueInd] = zj;
          }
          offset += useAllPreds?coeffPClass:_activeColsAll[classInd].length;
        }
        
        int idx = 0;
        int offsetIcpt = 0;
         if (hasIntercept) {
             for (int classInd = 0; classInd < _nclass; classInd++) {
               idx = useAllPreds?(coeffPClass*(classInd+1)-1):(_activeColsAll[classInd].length+offsetIcpt-1);
               double icpt = x[idx];
               if (lb != null && icpt < lb[idx])
                 icpt = lb[idx];
               if (ub != null && icpt > ub[idx])
                 icpt = ub[idx];
               double r = x[idx] - icpt;
               double s = icpt - z[idx];
               u[idx] += r;
               beta_given[idx] = icpt - u[idx];
               rnorm += r * r;  // r=0, not changed
               snorm += s * s;
               xnorm += icpt * icpt;
               unorm += rho[idx] * rho[idx] * u[idx] * u[idx]; // not changed since rho=0
               z[idx] = icpt; // z at intercept is not changed
               offsetIcpt += useAllPreds?0:_activeColsAll[classInd].length;
             }
           }

        if (rnorm < (abstol + (reltol * Math.sqrt(xnorm))) && snorm < (abstol + reltol * Math.sqrt(unorm))) {
          double oldGerr = gerr;
          computeErr(z, solver.gradient(z)._gradient, l1pen, lb, ub);
          if ((gerr > _eps) /* || solver.improving() */){// && (allzeros || i < 5 /* let some warm up before giving up */ /*|| Math.abs(oldGerr - gerr) > _eps * 0.1*/)) {
            Log.debug("ADMM.L1Solver: iter = " + i + " , gerr =  " + gerr + ", oldGerr = " + oldGerr + ", rnorm = " + rnorm + ", snorm  " + snorm);
            if(abstol > 1e-12) abstol *= .1;
            if(reltol > 1e-10) reltol *= .1;
            reltol *= .1;
            continue;
          }
          if(gerr > _eps)
            Log.warn("ADMM solver finished with gerr = " + gerr + " >  eps = " + _eps);
          iter = i;
          if(_pm != null && (i + 1) % 5 == 0)_pm.progress(z,solver.gradient(z));
          return true;
        }
      }
      if (hasIntercept) { // fill in the intercept values for z which now holds beta
        int offset = 0;
        int idx = 0;
        for (int classInd = 0; classInd < _nclass; classInd++) {
          idx = useAllPreds?(coeffPClass * (classInd+1) - 1):(_activeColsAll[classInd].length+offset-1);
          double icpt = x[idx];
          if (lb != null && icpt < lb[idx])
            icpt = lb[idx];
          if (ub != null && icpt > ub[idx])
            icpt = ub[idx];
          z[idx] = icpt;
          offset += useAllPreds?0:_activeColsAll[classInd].length;
        }
      }
      computeErr(z, solver.gradient(z)._gradient, l1pen, lb, ub);
      if(iter == max_iter)
        Log.warn("ADMM solver reached maximum number of iterations (" + max_iter + ")");
      else
        Log.warn("ADMM solver stopped after " + i + " iterations. (max_iter=" + max_iter + ")");
     if(gerr > _eps) Log.warn("ADMM solver finished with gerr = " + gerr + " >  eps = " + _eps);
      iter = max_iter;
      if(_pm != null && (i + 1) % 5 == 0)_pm.progress(z,solver.gradient(z));
      return false;
    }

    @Override public String toString(){
      return "iter = " + iter + ", gerr = " + gerr;
    }
    /**
     * Estimate optimal rho based on l1 penalty and (estimate of) solution x without the l1penalty
     * @param x:
     * @param l1pen
     * @return
     */
    public static double estimateRho(double x, double l1pen, double lb, double ub){
      if(Double.isInfinite(x))return 0; // happens for all zeros
      double rho = 0;
      if(l1pen != 0 && x != 0) {
        if (x > 0) {
          double D = l1pen * (l1pen + 4 * x);
          if (D >= 0) {
            D = Math.sqrt(D);
            double r = (l1pen + D) / (2 * x);
            if (r > 0) rho = r;
            else
              Log.warn("negative rho estimate(1)! r = " + r);
          }
        } else if (x < 0) {
          double D = l1pen * (l1pen - 4 * x);
          if (D >= 0) {
            D = Math.sqrt(D);
            double r = -(l1pen + D) / (2 * x);
            if (r > 0) rho = r;
            else Log.warn("negative rho estimate(2)!  r = " + r);
          }
        }
        rho *= .25;
      }
      if(!Double.isInfinite(lb) || !Double.isInfinite(ub)) {
        boolean oob = -Math.min(x - lb, ub - x) > -1e-4;
        rho = oob?10:1e-1;
      }
      return rho;
    }
  }

  public static double shrinkage(double x, double kappa) {
    double sign = x < 0?-1:1;
    double sx = x*sign;
    return sx <= kappa?0:sign*(sx - kappa);
  }

  public static void subgrad(final double lambda, final double [] beta, final double [] grad){
    if(beta == null)return;
    for(int i = 0; i < grad.length-1; ++i) {// add l2 reg. term to the gradient
      if(beta[i] < 0) grad[i] = shrinkage(grad[i]-lambda,lambda*1e-4);
      else if(beta[i] > 0) grad[i] = shrinkage(grad[i] + lambda,lambda*1e-4);
      else grad[i] = shrinkage(grad[i], lambda);
    }
  }
}
