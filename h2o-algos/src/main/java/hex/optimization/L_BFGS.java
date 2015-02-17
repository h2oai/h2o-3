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
 *
 * Generic L-BFGS optmizer implementation.
 *
 * NOTE: The solver object keeps its state and so the same object can not be reused to solve different problems.
 * (but can be used for warm-starting/continuation of the same problem)
 *
 * Usage:
 *
 * To apply L-BFGS to your optimization problem, provide a GradientSolver with following 2 methods:
 *   1) double [] getGradient(double []):
 *      evaluate gradient at given coefficients, typically an MRTask
 *   2) double [] getObjVals(double[] beta,double[] direction):
 *      evaluate objective value at line-search search points (e.g. objVals[k] = obj(beta + step(k)*direction), step(k) = .75^k)
 *      typically a single MRTask
 *   @see hex.glm.GLM.GLMGradientSolver
 *
 * L-BFGS will then perform following loop:
 *   while(not converged):
 *     coefs    := doLineSearch (coefs, dir)  // distributed, 1 pass over data
 *     gradient := getGradient(coefs)         // distributed, 1 pass over data
 *     history  += (coefs, gradient)          // local
 *     dir      := newDir(history, gradient)  // local
 *
 * 1 L-BFGS iteration thus takes 2 passes over the (distributed) dataset.
 *
*/
public final class L_BFGS extends Iced {
  int _maxIter = 500;
  double _gradEps = 1e-8;
  // line search params
  int _historySz = 20;

  History _hist;

  public L_BFGS() {}
  public L_BFGS setMaxIter(int m) {_maxIter = m; return this;}
  public L_BFGS setGradEps(double d) {_gradEps = d; return this;}
  public L_BFGS setHistorySz(int sz) {_historySz = sz; return this;}


  public int k() {return _hist._k;}
  public int maxIter(){ return _maxIter;}

  public static class GradientInfo extends Iced {
    public double _objVal;
    public final double [] _gradient;

    public GradientInfo(double objVal, double [] grad){
      _objVal = objVal;
      _gradient = grad;
    }

    public boolean isValid(){
      if(Double.isNaN(_objVal))
        return false;
      return !ArrayUtils.hasNaNsOrInfs(_gradient);
    }
    @Override
    public String toString(){
      return " objVal = " + _objVal + ", " + Arrays.toString(_gradient);
    }

    public boolean hasNaNsOrInfs() {
      return Double.isNaN(_objVal) || ArrayUtils.hasNaNsOrInfs(_gradient);
    }
  }

  /**
   *  Provides gradient computation and line search evaluation specific to given problem.
   *  Typically just a wrapper around MRTask calls.
   */
  public static abstract class GradientSolver {

    /**
     * Evaluate gradient at solution beta.
     * @param beta
     * @return
     */
    public abstract GradientInfo  getGradient(double [] beta);

    /**
     * Evaluate objective values at k line search points beta_k.
     *
     * When used as part of default line search behavior, the line search points are expected to be
     *     beta_k = beta + direction * _startStep * _stepDec^k
     *
     * @param beta - initial vector of coefficients
     * @param pk   - search direction
     * @return objective values evaluated at k line-search points beta + pk*step[k]
     */
    protected abstract double [] getObjVals(double[] beta, double[] pk);

    protected double _startStep = 1.0;
    protected double _stepDec = .75;

    /**
     * Perform line search at given solution and search direction.
     *
     * @param ginfo     - gradient and objective value at current solution
     * @param beta      - current solution
     * @param direction - search direction
     * @return
     */
    public LineSearchSol doLineSearch(GradientInfo ginfo, double [] beta, double [] direction) {
      double [] objVals = getObjVals(beta, direction);
      double t = _startStep, tdec = _stepDec;
      for (int i = 0; i < objVals.length; ++i) {
        if (admissibleStep(t, ginfo._objVal, objVals[i], direction, ginfo._gradient))
          return new LineSearchSol(true, objVals[i], t);
        t *= tdec;
      }
      return new LineSearchSol(false, objVals[objVals.length-1], t);
    }
  }

  /**
   * Monitor progress and enable early termination.
   */
  public static class ProgressMonitor {
    public boolean progress(double [] beta, GradientInfo ginfo){return true;}
  }

  // constants used in line search
  public static final double c1 = .5;

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
  public static final class History extends Iced {
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
    double [] getY(int k){ return _y[(_k + k) % _m];}
    double [] getS(int k){ return _s[(_k + k) % _m];}
    double rho(int k){return _rho[(_k + k) % _m];}

    int _k;

    private final void update(double [] pk, double [] gNew, double [] gOld){
      int id = _k % _m;
      final double[] gradDiff = _y[id];
      for (int i = 0; i < gNew.length; ++i)
        gradDiff[i] = gNew[i] - gOld[i];
      System.arraycopy(pk,0,_s[id],0,pk.length);
      _rho[id] = 1.0/ArrayUtils.innerProduct(_s[id],_y[id]);
      ++_k;
    }

    // the actual core of L-BFGS algo
    // compute new search direction using the gradient at current beta and history
    protected  final double [] getSearchDirection(final double [] gradient) {
      double [] alpha = MemoryManager.malloc8d(_m);
      double [] q = gradient.clone();
      for (int i = 1; i <= Math.min(_k,_m); ++i) {
        alpha[i-1] = rho(-i) * ArrayUtils.innerProduct(getS(-i), q);
        MathUtils.wadd(q, getY( - i), -alpha[i - 1]);
      }
      if(_k > 0) {
        final double [] s = getS(-1);
        final double [] y = getY(-1);
        double Hk0 = ArrayUtils.innerProduct(s,y) / ArrayUtils.innerProduct(y, y);
        ArrayUtils.mult(q, Hk0);
      }
      for (int i = Math.min(_k,_m); i > 0; --i) {
        double beta = rho(-i)*ArrayUtils.innerProduct(getY(-i),q);
        MathUtils.wadd(q,getS(-i),alpha[i-1]-beta);
      }
      return ArrayUtils.mult(q,-1);
    }

  }

  /**
   * Solve the optimization problem defined by the user-supplied gradient function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided gradient function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The gradient is likely to be the most expensive part and key for good perfomance.
   *
   * @param gslvr  - user gradient function
   * @params coefs - intial solution
   * @return Optimal solution (coefficients) + gradient info returned by the user gradient
   * function evaluated at the found optmimum.
   */
  public final Result solve(GradientSolver gslvr, double [] coefs){
    return solve(gslvr, coefs, gslvr.getGradient(coefs), new ProgressMonitor());
  }

  /**
   * Solve the optimization problem defined by the user-supplied gradient function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided gradient function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The gradient is likely to be the most expensive part and key for good perfomance.
   *
   * @param gslvr - user gradient function
   * @param beta - starting solution
   * @return Optimal solution (coefficients) + gradient info returned by the user gradient
   * function evaluated at the found optmimum.
   */
  public final Result solve(GradientSolver gslvr, double [] beta, GradientInfo ginfo, ProgressMonitor pm) {
    if(_hist == null)
      _hist = new History(_historySz, beta.length);
    beta = beta.clone();
    // just loop until good enough or line search can not progress
    int iter = 0;
    while(pm.progress(beta, ginfo) && MathUtils.l2norm2(ginfo._gradient) > _gradEps && iter != _maxIter) {
      double [] pk = _hist.getSearchDirection(ginfo._gradient);
      LineSearchSol ls = gslvr.doLineSearch(ginfo, beta, pk);
      if(ls.madeProgress || _hist._k < _hist._m) {
        ArrayUtils.mult(pk,ls.step);
        ++iter; // only count successful iterations
        ArrayUtils.add(beta, pk);
        GradientInfo newGinfo = gslvr.getGradient(beta); // expensive / distributed
        _hist.update(pk, newGinfo._gradient, ginfo._gradient);
        ginfo = newGinfo;
      } else
        break; // line search did not make any progress
    }
    Log.info("L_BFGS done after " + iter + " iterations, objval = " + ginfo._objVal + ", gradient norm2 = " + MathUtils.l2norm2(ginfo._gradient) );
    return new Result(iter,beta, ginfo);
  }

  public static double [] startCoefs(int n, long seed){
    double [] res = MemoryManager.malloc8d(n);
    Random r = new Random(seed);
    for(int i = 0; i < res.length; ++i)
      res[i] = r.nextGaussian();
    return res;
  }

  /**
   * Line search results.
   */
  public static class LineSearchSol {
    public final double objVal;        // objective value at the step
    public final double step;          // returned line search step size
    public final boolean madeProgress; // true if the step is admissible

    public LineSearchSol(boolean progress, double obj, double step) {
      objVal = obj;
      this.step = step;
      madeProgress = progress;
    }
  }

  // Armijo line-search rule
  private static final boolean admissibleStep(double step, final double objOld, final double objNew, final double[] pk, final double[] gradOld){
    if(Double.isNaN(objNew))
      return false;
    // line search
    double f_hat = 0;
    for(int i = 0; i < pk.length; ++i)
      f_hat += gradOld[i] * pk[i];
    f_hat = c1*step*f_hat + objOld;
    return objNew < f_hat;
  }

}
