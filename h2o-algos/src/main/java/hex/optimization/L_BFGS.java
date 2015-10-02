package hex.optimization;

import hex.glm.GLM;
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
 * Generic L-BFGS optimizer implementation.
 *
 * NOTE: The solver object keeps its state and so the same object can not be reused to solve different problems.
 * (but can be used for warm-starting/continuation of the same problem)
 *
 * Usage:
 *
 * To apply L-BFGS to your optimization problem, provide a GradientSolver with following 2 methods:
 *   1) double [] getGradient(double []):
 *      evaluate gradient at given coefficients, typically an MRTask
 *   2) double [] getObjVals(double[] beta, double[] direction):
 *      evaluate objective value at line-search search points (e.g. objVals[k] = obj(beta + step(k)*direction), step(k) = .75^k)
 *      typically a single MRTask
 *   @see hex.glm.GLM.GLMGradientSolver
 *
 * L-BFGS will then perform following loop:
 *   while(not converged):
 *     coefs    := doLineSearch(coefs, dir)   // distributed, 1 pass over data
 *     gradient := getGradient(coefs)         // distributed, 1 pass over data
 *     history  += (coefs, gradient)          // local
 *     dir      := newDir(history, gradient)  // local
 *
 * 1 L-BFGS iteration thus takes 2 passes over the (distributed) dataset.
 *
*/
public final class L_BFGS extends Iced {
  int _maxIter = 500;
  int _minIter = 0;
  double _gradEps = 1e-8;
  double _objEps = 1e-4;
  // line search params
  int _historySz = 20;

  History _hist;

  public L_BFGS() {}
  public L_BFGS setMaxIter(int m) {_maxIter = m; return this;}
  public L_BFGS setMinIter(int m) {_minIter = m; return this;}
  public L_BFGS setGradEps(double d) {_gradEps = d; return this;}
  public L_BFGS setObjEps(double d) {_objEps = d; return this;}
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
    public abstract double [] getObjVals(double[] beta, double[] pk, int nSteps, double initialStep, double stepDec);


    /**
     * Perform line search at given solution and search direction.
     *
     * @param ginfo     - gradient and objective value at current solution
     * @param beta      - current solution
     * @param direction - search direction
     * @return
     */
    public LineSearchSol doLineSearch(GradientInfo ginfo, double [] beta, double [] direction, int nSteps, double tdec) {
      double [] objVals = null;
      double t = 1;
      while(t > GLM.MINLINE_SEARCH_STEP) {
        objVals = getObjVals(beta, direction, nSteps, t, tdec);
        for (int i = 0; i < objVals.length; ++i) {
          if (admissibleStep(t, ginfo._objVal, objVals[i], direction, ginfo._gradient))
            return new LineSearchSol(true, objVals[i], t);
          t *= tdec;
        }
      }
      return new LineSearchSol(objVals[objVals.length-1] < ginfo._objVal, objVals[objVals.length-1], t/tdec);
    }
  }

  /**
   * Monitor progress and enable early termination.
   */
  public static class ProgressMonitor {
    public boolean progress(double [] beta, GradientInfo ginfo){return true;}
  }

  // constants used in line search
  public static final double c1 = .25;

  public static final class Result {
    public final int iter;
    public final double [] coefs;
    public final GradientInfo ginfo;
    public final boolean converged;

    public Result(boolean converged, int iter, double [] coefs, GradientInfo ginfo){
      this.iter = iter;
      this.coefs = coefs;
      this.ginfo = ginfo;
      this.converged = converged;
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
   * @params coefs - initial solution
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
   * The gradient is likely to be the most expensive part and key for good performance.
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
    boolean doLineSearch = true;
    int ls_switch = 0;
    double rel_improvement = 1;
    boolean converged = false;
    while(pm.progress(beta, ginfo) &&  (iter < _minIter || ArrayUtils.linfnorm(ginfo._gradient,false) > _gradEps  && rel_improvement > _objEps) && iter != _maxIter) {
      double [] pk = _hist.getSearchDirection(ginfo._gradient);
      if(ArrayUtils.hasNaNsOrInfs(pk)) {
        Log.warn("LBFGS: Got NaNs in search direction.");
        break; //
      }
      LineSearchSol ls = null;

      if(doLineSearch) {
        ls = gslvr.doLineSearch(ginfo, beta, pk, 24, .5);
        if(ls.step == 1) {
          if (++ls_switch == 2) {
            ls_switch = 0;
            doLineSearch = false;
          }
        } else {
          ls_switch = 0;
        }
        if (ls.madeProgress || _hist._k < 2) {
          ArrayUtils.wadd(beta, pk, ls.step);
        } else {
          break; // ls did not make progress => converged
        }
      } else  ArrayUtils.add(beta, pk);
      GradientInfo newGinfo = gslvr.getGradient(beta); // expensive / distributed
      if(doLineSearch && !(Double.isNaN(ls.objVal) && Double.isNaN(newGinfo._objVal)) && Math.abs(ls.objVal - newGinfo._objVal) > 1e-10*ls.objVal) {
        throw new IllegalArgumentException("L-BFGS: Got invalid gradient solver, objective values from line-search and gradient tasks differ, " + ls.objVal + " != " + newGinfo._objVal + ", step = " + ls.step);
      }
      if(!doLineSearch) //{
        if(!admissibleStep(1,ginfo._objVal,newGinfo._objVal,pk,ginfo._gradient)) {
          if(++ls_switch == 2) {
            doLineSearch = true;
            ls_switch = 0;
          }
          if(ginfo._objVal < newGinfo._objVal && (newGinfo._objVal - ginfo._objVal > _objEps*ginfo._objVal)) {
            doLineSearch = true;
            ArrayUtils.subtract(beta,pk,beta);
            continue;
          }
        } else ls_switch = 0;
      ++iter;
      _hist.update(pk, newGinfo._gradient, ginfo._gradient);
      rel_improvement = (ginfo._objVal - newGinfo._objVal)/ginfo._objVal;
      ginfo = newGinfo;
    }
    return new Result(iter < _maxIter || ArrayUtils.linfnorm(ginfo._gradient,false) < _gradEps || rel_improvement < _objEps,iter,beta, ginfo);
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
