package hex.optimization;

import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 * Created by tomasnykodym on 3/2/15.
 */
public class ADMM {

  public interface ProximalSolver {
    public double []  rho();
    public void solve(double [] beta_given, double [] result);
    public boolean hasGradient();
    public double [] gradient(double [] beta);
    public void setRho(double [] rho);
    public boolean canSetRho();
  }

  public static class L1Solver {
    final static double RELTOL = 1e-2;
    final static double ABSTOL = 1e-4;
    double gerr;
    int iter;
    final double _eps;
    final int max_iter;

    public L1Solver(double eps, int max_iter) {
      _eps = eps; this.max_iter = max_iter;
    }

    public boolean solve(ProximalSolver solver, double[] res, double lambda) {
      return solve(solver, res, lambda, true, null, null);
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
      for (int x = 0; x < grad.length - 1; ++x) {
        double err = grad[x];
        if(err < 0) err = -err;
        if(gerr < err) gerr = err;
      }
      return gerr;
    }

    public boolean solve(ProximalSolver solver, double[] z, double l1pen, boolean hasIntercept, double[] lb, double[] ub) {
      gerr = Double.POSITIVE_INFINITY;
      if (l1pen == 0 && lb == null && ub == null) {
        solver.solve(null, z);
        return true;
      }
      int ii = hasIntercept?1:0;
      double[] zbest = null;
      int N = z.length;
      double abstol = ABSTOL * Math.sqrt(N);
      double [] rho = solver.rho();
      double[] u = MemoryManager.malloc8d(N);
      double[] x = MemoryManager.malloc8d(N);
      double[] beta_given = MemoryManager.malloc8d(N);
      double  [] kappa = MemoryManager.malloc8d(rho.length);

      if(l1pen > 0)
        for(int i = 0; i < N-ii; ++i)
          kappa[i] = l1pen/rho[i];
      int i;
      double orlx = 1.0; // over-relaxation
      double reltol = RELTOL;
      double best_err = Double.POSITIVE_INFINITY;
      for (i = 0; i < max_iter; ++i) {
        // updated x
        solver.solve(beta_given, x);
        // compute u and z updateADMM
        double rnorm = 0, snorm = 0, unorm = 0, xnorm = 0;
        boolean allzeros = true;
        for (int j = 0; j < N - ii; ++j) {
          double xj = x[j];
          double zjold = z[j];
          double x_hat = xj * orlx + (1 - orlx) * zjold;
          double zj = shrinkage(x_hat + u[j], kappa[j]);
          if (lb != null && zj < lb[j])
            zj = lb[j];
          if (ub != null && zj > ub[j])
            zj = ub[j];
          u[j] += x_hat - zj;
          beta_given[j] = zj - u[j];
          double r = xj - zj;
          double s = zj - zjold;
          rnorm += r * r;
          snorm += s * s;
          xnorm += xj * xj;
          unorm += rho[j] * rho[j] * u[j] * u[j];
          z[j] = zj;
          allzeros &= zj == 0;
        }
        if (hasIntercept) { // TODO update unorm and so on
          int idx = x.length - 1;
          double icpt = x[idx];
          if (lb != null && icpt < lb[idx])
            icpt = lb[idx];
          if (ub != null && icpt > ub[idx])
            icpt = ub[idx];
          double r = x[idx] - icpt;
          double s = icpt - z[idx];
          u[idx] += r;
          beta_given[idx] = icpt - u[idx];
          rnorm += r * r;
          snorm += s * s;
          xnorm += icpt * icpt;
          unorm += rho[idx] * rho[idx] * u[idx] * u[idx];
          z[idx] = icpt;
        }
        if (rnorm < (abstol + (reltol * Math.sqrt(xnorm))) && snorm < (abstol + reltol * Math.sqrt(unorm))) {
          double oldGerr = gerr;
          computeErr(z, solver.gradient(z), l1pen, lb, ub);
          if (gerr > _eps && (allzeros || Math.abs(oldGerr - gerr) > _eps * 0.5)) {
            Log.debug("ADMM.L1Solver: iter = " + i + " , gerr =  " + gerr + ", oldGerr = " + oldGerr + ", rnorm = " + rnorm + ", snorm  " + snorm);
            // try gg to improve the solution...
            abstol *= .1;
            if (abstol < 1e-10)
              abstol = 1e-10;
            reltol *= .1;
            if (reltol < 1e-10)
              reltol = 1e-10;
            continue;
          }
          iter = i;
          Log.info("ADMM.L1Solver: converged at iteration = " + i + ", gerr = " + gerr);
          return true;
        }
      }
      computeErr(z, solver.gradient(z), l1pen, lb, ub);
      if (zbest != null && best_err < gerr) {
        System.arraycopy(zbest, 0, z, 0, zbest.length);
        computeErr(z, solver.gradient(z), l1pen, lb, ub);
        assert Math.abs(best_err - gerr) < 1e-8 : " gerr = " + gerr + ", best_err = " + best_err + " zbest = " + Arrays.toString(zbest) + ", z = " + Arrays.toString(z);
      }
      Log.warn("ADMM DID NOT CONVERGE with gerr = " + gerr);
      iter = max_iter;
      return false;
    }

    /**
     * Estimate optimal rho based on l1 penalty and (estimate of) soltuion x without the l1penalty
     * @param x
     * @param l1pen
     * @return
     */
    public static double estimateRho(double x, double l1pen){
      double rho = 0;
      if(l1pen == 0 || x == 0) return 0;
      if (x > 0) {
        double D = l1pen * (l1pen + 4 * x);
        if (D >= 0) {
          D = Math.sqrt(D);
          double r = .25 * (l1pen + D) / (2 * x);
          if (r > 0) rho = r;
          else Log.warn("negative rho estimate(1)! r = " + r);
        }
      } else if (x < 0) {
        double D = l1pen * (l1pen - 4 * x);
        if (D >= 0) {
          D = Math.sqrt(D);
          double r = -.25 * (l1pen + D) / (2 * x);
          if (r > 0) rho = r;
          else Log.warn("negative rho estimate(2)!  r = " + r);
        }
      }
      return rho;
    }
  }

  public static double shrinkage(double x, double kappa) {
    double sign = x < 0?-1:1;
    double sx = x*sign;
    if(sx <= kappa) return 0;
    return sign*(sx - kappa);
  }

  public static void subgrad(final double lambda, final double [] beta, final double [] grad){
    if(beta == null)return;
    for(int i = 0; i < grad.length-1; ++i) {// add l2 reg. term to the gradient
      if(beta[i] < 0) grad[i] = shrinkage(grad[i]-lambda,lambda*1e-3);
      else if(beta[i] > 0) grad[i] = shrinkage(grad[i] + lambda,lambda*1e-3);
      else grad[i] = shrinkage(grad[i], 1.001*lambda);
    }
  }
}
