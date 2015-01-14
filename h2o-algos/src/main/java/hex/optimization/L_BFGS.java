package hex.optimization;

import water.Iced;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;
import java.util.Random;

/**
 * Created by tomasnykodym on 9/15/14.
 * L-BFGS optmizer implementation.
 *
 * Use by calling solve() and passing in your own gradient computation function.
 *
*/
public class L_BFGS  {
  public static class GradientInfo {
    public final double _objVal;
    public final double [] _gradient;

    public GradientInfo(double objVal, double [] grad){
      _objVal = objVal;
      _gradient = grad;
    }
    @Override
    public String toString(){
      return " objVal = " + _objVal + ", " + Arrays.toString(_gradient);
    }
  }

  /**
   * To be overriden to provide gradient computation specific for given problem.
   */
  public static abstract class GradientSolver {
    public abstract GradientInfo [] getGradient(double [][] betas);
    public final GradientInfo getGradient(double [] betas){
      return getGradient(new double[][]{betas})[0];
    }
  }
  // constants used in line search
  public static final double c1 = 1e-1;

  public static final class Result {
    public final int iter;
    public final double [] coefs;
    public final GradientInfo ginfo;

    public Result(int iter, double [] coefs, GradientInfo ginfo){
      this.iter = iter;
      this.coefs = coefs;
      this.ginfo = ginfo;
    }

    public String toString(){
      return coefs.length < 50?
        "L-BFGS_res(iter = " + iter + ", obj = " + ginfo._objVal + ", " + " coefs = " + Arrays.toString(coefs) + ", grad = " + Arrays.toString(ginfo._gradient) + ")"
        :("L-BFGS_res(iter = " + iter + ", obj = " + ginfo._objVal + ", coefs = [" + coefs[0] + ", " + coefs[1] + ", ..., " + coefs[coefs.length-2] + ", " + coefs[coefs.length-1] + "]" +
        ", grad = [" + ginfo._gradient[0] + ", " + ginfo._gradient[1] + ", ..., " + ginfo._gradient[ginfo._gradient.length-2] + ", " + ginfo._gradient[ginfo._gradient.length-1] + "])") +
        "|grad|^2 = " + MathUtils.l2norm2(ginfo._gradient);
    }
  }

  /**
   *  Keeps L-BFGS history ie curvature information recorded over the last m steps.
   */
  public static final class History {
    private final double [][] _s;
    private final double [][] _y;
    private final double [] _rho;
    final int _m, _n;

    public History(int m, int n) {
      _m = m;
      _n = n;
      _s = new double[m][];
      _y = new double[m][];
      _rho = MemoryManager.malloc8d(m);
      Arrays.fill(_rho,Double.NaN);
      for (int i = 0; i < m; ++i) {
        _s[i] = MemoryManager.malloc8d(n);
        Arrays.fill(_s[i], Double.NaN); // to make sure we don't just run with zeros
        _y[i] = MemoryManager.malloc8d(n);
        Arrays.fill(_y[i], Double.NaN);
      }
    }
    double [] getY(int k){ return _y[k % _m];}
    double [] getS(int k){ return _s[k % _m];}
    double rho(int i){return _rho[i % _m];}

    private final void update(int iter, double [] pk, double [] gNew, double [] gOld){
      assert(iter >= 0);
      int id = iter % _m;
      final double[] gradDiff = _y[id];
      for (int i = 0; i < gNew.length; ++i)
        gradDiff[i] = gNew[i] - gOld[i];
      System.arraycopy(pk,0,_s[id],0,pk.length);
      _rho[id] = 1.0/ArrayUtils.innerProduct(_s[id],_y[id]);
    }
  }

  /**
   * Internal parameters affecting behavior of L-BFGS solver.
   * Contains parameters affecting number of iterations, conevrgence criterium and line search details.
   */
  public static final class L_BFGS_Params extends Iced {
    public int _maxIter = 1000;
    public double _gradEps = 1e-5;
    // line search params
    public double _minStep = 1e-3;
    public int _nBetas = 8; // number of line search steps done in each pass (to minimize passes over the whole data)
    public double _stepDec = .8; // line search step decrement

  }
  /**
   * Solve the optimization problem defined by the user-supplied gradient function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided gradient function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The gradient is likely to be the most expensive part and key for good perfomance.
   *
   * @param n      - number of coefficients
   * @param gslvr  - user gradient function
   * @param params - internal L-BFGS parameters.
   * @return Optimal solution (coefficients) + gradient info returned by the user gradient
   * function evaluated at the found optmimum.
   */
  public static final Result solve(int n, GradientSolver gslvr, L_BFGS_Params params){
    double [] coefs = startCoefs(n);
    return solve(gslvr,params, new History(20,coefs.length),coefs);
  }

  /**
   * Solve the optimization problem defined by the user-supplied gradient function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided gradient function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The gradient is likely to be the most expensive part and key for good perfomance.
   *
   * @param gslvr - user gradient function
   * @param hist  - history of computation
   * @param coefs - starting solution
   * @return Optimal solution (coefficients) + gradient info returned by the user gradient
   * function evaluated at the found optmimum.
   */
  public static final Result solve(GradientSolver gslvr, final L_BFGS_Params params, History hist,final double [] coefs) {
    GradientInfo gOld = gslvr.getGradient(coefs);
    final double [] beta = coefs;
    int iter = 0;
    double [][] lsBetas = new double[params._nBetas][]; // do 32 line-search steps at once to minimize passes through the whole dataset
    for(int i = 0; i < lsBetas.length; ++i)
      lsBetas[i] = MemoryManager.malloc8d(beta.length);
    double step = 1;
    // jsut loop until good enough or line search can not progress
_MAIN:
    while(iter++ < params._maxIter && MathUtils.l2norm2(gOld._gradient) > params._gradEps) {
      double[] pk = getSearchDirection(iter-1, hist, gOld._gradient);
      double t = step;
      while (t > params._minStep) {
        for (int i = 0; i < params._nBetas; ++i) {
          wadd(lsBetas[i], beta, pk, t);
          t *= params._stepDec;
        }
        GradientInfo[] ginfos = gslvr.getGradient(lsBetas);
        t = step;
        // check the line search, we do several steps at once each time to limit number of passes over all data
        for (int i = 0; i < ginfos.length; ++i) {
          if (t <= params._minStep || !needLineSearch(t, gOld._objVal, ginfos[i]._objVal, pk, gOld._gradient)) {
            // we got admissible solution
            ArrayUtils.mult(pk, t);
            if(iter > 0)
              hist.update(iter-1, pk, ginfos[i]._gradient, gOld._gradient);
            gOld = ginfos[i];
            ArrayUtils.add(beta, pk);
            assert Arrays.equals(beta, lsBetas[i]);
            step = 1; // reset line search to start from step = 1 again
            continue _MAIN;
          }
          t *= params._stepDec;
        }
        step = t;
      }
      // line search did not progress -> converged
      break _MAIN;
    }
    Log.info("L_BFGS done after " + iter + " iterations");
    return new Result(iter,beta, gOld);
  }

  // the actual core of L-BFGS algo
  private static final double [] getSearchDirection(final int iter, final History hist, final double [] gradient) {
    // get search direction
    double[] alpha = MemoryManager.malloc8d(hist._m);
    double [] q = gradient.clone();
    for (int i = 1; i <= Math.min(iter,hist._m); ++i) {
      alpha[i-1] = hist.rho(iter-i) * ArrayUtils.innerProduct(hist.getS(iter-i), q);
      MathUtils.wadd(q, hist.getY(iter - i), -alpha[i - 1]);
    }
    if(iter > 0) {
      final double [] s = hist.getS(iter - 1);
      final double [] y = hist.getY(iter - 1);
      double Hk0 = ArrayUtils.innerProduct(s,y) / ArrayUtils.innerProduct(y, y);
      ArrayUtils.mult(q, Hk0);
    }
    for (int i = Math.min(iter,hist._m); i > 0; --i) {
      double beta = hist.rho(iter-i)*ArrayUtils.innerProduct(hist.getY(iter-i),q);
      MathUtils.wadd(q,hist.getS(iter-i),alpha[i-1]-beta);
    }
    ArrayUtils.mult(q,-1);
    // q now has the search direction
    return q;
  }

  private static final double [] wadd(double [] res, double [] x, double [] y, double w){
    for(int i = 0; i < x.length; ++i)
      res[i] = x[i] +  w*y[i];
    return x;
  }
  private static double [] startCoefs(int n){
    double [] res = MemoryManager.malloc8d(n);
    Random r = new Random();
    for(int i = 0; i < res.length; ++i)
      res[i] = r.nextGaussian();
    return res;
  }

  // Armijo line-search rule
  private static final boolean needLineSearch(double step, final double objOld, final double objNew, final double [] pk, final double [] gradOld){
    // line search
    double f_hat = 0;
    for(int i = 0; i < pk.length; ++i)
      f_hat += gradOld[i] * pk[i];
    f_hat = c1*step*f_hat + objOld;
    return objNew > f_hat;
  }

}
