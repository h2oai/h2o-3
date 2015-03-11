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
    final static double RELTOL = 1e-4;
    final static double ABSTOL = 1e-8;
    double gerr;
    int iter;
    final double _eps;
    final int max_iter;

    public L1Solver(){this(1e-4,5000);}
    public L1Solver(double eps){this(eps,5000);}
    public L1Solver(double eps, int max_iter){_eps = eps; this.max_iter = max_iter;}

    public boolean solve(ProximalSolver solver, double[] res, double lambda) {
      return solve(solver, res, lambda, true, null, null);
    }

    private double computeErr(double[] z, double[] grad, double lambda, double[] lb, double[] ub) {
      grad = grad.clone();
      // check the gradient
      gerr = 0;
      if (lb != null)
        for (int j = 0; j < z.length; ++j)
          if (z[j] == lb[j] && grad[j] < 0)
            grad[j] = 0;
      if (ub != null)
        for (int j = 0; j < z.length; ++j)
          if (z[j] == ub[j] && grad[j] > 0)
            grad[j] = 0;
      subgrad(lambda, z, grad);
      for (int x = 0; x < grad.length - 1; ++x) {
        double err = grad[x];
        if(err < 0) err = -err;
        if(gerr < err) gerr = err;
      }
      return gerr;
    }

    public boolean solve(ProximalSolver solver, double[] z, double lambda, boolean hasIntercept, double[] lb, double[] ub) {
      gerr = Double.POSITIVE_INFINITY;
      if (lambda == 0 && lb == null && ub == null) {
        solver.solve(null, z);
        return true;
      }
      double[] zbest = null;
      int N = z.length;
      double abstol = ABSTOL * Math.sqrt(N);
      double [] rho = solver.rho();
      double[] u = MemoryManager.malloc8d(N);
      double[] x = MemoryManager.malloc8d(N);
      double[] beta_given = MemoryManager.malloc8d(N);
      double  [] kappa = MemoryManager.malloc8d(rho.length);
      for(int i = 0; i < kappa.length; ++i)
        kappa[i] = lambda/rho[i];
      int i;
      double orlx = 1.5; // over-relaxation
      double reltol = RELTOL;
      double best_err = Double.POSITIVE_INFINITY;
      for (i = 0; i < max_iter; ++i) {
        // updated x
        assert beta_given[beta_given.length - 1] == 0;
        solver.solve(beta_given, x);
        // compute u and z updateADMM
        double rnorm = 0, snorm = 0, unorm = 0, xnorm = 0;
        for (int j = 0; j < N - (hasIntercept ? 1 : 0); ++j) {
          double xj = x[j];
          double zjold = z[j];
          double x_hat = xj * orlx + (1 - orlx) * zjold;
          double zj = shrinkage(x_hat + u[j], kappa[j]);
          u[j] += x_hat - zj;
          beta_given[j] = zj - u[j];
          double r = xj - zj;
          double s = zj - zjold;
          rnorm += r * r;
          snorm += s * s;
          xnorm += xj * xj;
          unorm += rho[j] * rho[j] * u[j] * u[j];
          z[j] = zj;
        }
        if (hasIntercept)
          z[z.length - 1] = x[x.length - 1];

        if (solver.hasGradient() || rnorm < (abstol + (reltol * Math.sqrt(xnorm))) && snorm < (abstol + reltol * Math.sqrt(unorm))) {
          double oldGerr = gerr;
          computeErr(z, solver.gradient(z), lambda, lb, ub);
          if (gerr > _eps && Math.abs(oldGerr - gerr) > _eps * .5) {
            System.out.println("ADMM.L1Solver: iter = " + i + " , gerr =  " + gerr + ", oldGerr = " + oldGerr + ", rnorm = " + rnorm + ", snorm  " + snorm);
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
          System.out.println("ADMM.L1Solver: converged at iteration = " + i + ", gerr = " + gerr);
          return true;}
        }
        computeErr(z, solver.gradient(z), lambda, lb, ub);
        if (zbest != null && best_err < gerr) {
          System.arraycopy(zbest, 0, z, 0, zbest.length);
          computeErr(z, solver.gradient(z), lambda, lb, ub);
          assert Math.abs(best_err - gerr) < 1e-8 : " gerr = " + gerr + ", best_err = " + best_err + " zbest = " + Arrays.toString(zbest) + ", z = " + Arrays.toString(z);
        }
        System.out.println("ADMM DID NOT CONVERGE with gerr = " + gerr);
        iter = max_iter;
        return false;
      }
//  }

  }
  protected static double shrinkage(double x, double kappa) {
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
