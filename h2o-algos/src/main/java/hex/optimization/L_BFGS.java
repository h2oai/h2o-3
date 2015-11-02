package hex.optimization;

import hex.optimization.OptimizationUtils.*;
import water.Iced;
import water.MemoryManager;
import water.util.ArrayUtils;
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
 *      evaluate ginfo at given coefficients, typically an MRTask
 *   2) double [] getObjVals(double[] beta, double[] direction):
 *      evaluate objective value at line-search search points (e.g. objVals[k] = obj(beta + step(k)*direction), step(k) = .75^k)
 *      typically a single MRTask
 *   @see hex.glm.GLM.GLMGradientSolver
 *
 * L-BFGS will then perform following loop:
 *   while(not converged):
 *     coefs    := doLineSearch(coefs, dir)   // distributed, 1 pass over data
 *     ginfo := getGradient(coefs)         // distributed, 1 pass over data
 *     history  += (coefs, ginfo)          // local
 *     dir      := newDir(history, ginfo)  // local
 *
 * 1 L-BFGS iteration thus takes 2 passes over the (distributed) dataset.
 *
*/
public final class L_BFGS extends Iced {
  int _maxIter = 500;
  double _gradEps = 1e-8;
  double _objEps = 1e-10;
  // line search params
  int _historySz = 20;

  History _hist;

  LineSearchSolver _lineSearch = new MoreThuente();// new BacktrackingLS(.5);//new MoreThuente();

  public L_BFGS() {}
  public L_BFGS setMaxIter(int m) {_maxIter = m; return this;}
  public L_BFGS setGradEps(double d) {_gradEps = d; return this;}
  public L_BFGS setObjEps(double d) {
    _objEps = d; return this;
  }
  public L_BFGS setHistorySz(int sz) {_historySz = sz; return this;}


  public int k() {return _hist._k;}
  public int maxIter(){ return _maxIter;}



  /**
   * Monitor progress and enable early termination.
   */
  public static class ProgressMonitor {
    public boolean progress(double [] beta, GradientInfo ginfo){return true;}
  }

  // constants used in line search

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
    private final double [] _alpha;

    final int _m, _n;

    public History(int m, int n) {
      _m = m;
      _alpha = new double[_m];
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
    int getId(int k) {return (_k + k) % _m;}

    int _k;

    private final void update(double [] pk, double [] gNew, double [] gOld){
      int id = getId(0);
      double[] y = _y[id];
      double[] s = _s[id];
      for (int i = 0; i < gNew.length; ++i)
        y[i] = gNew[i] - gOld[i];
      System.arraycopy(pk,0,s,0,pk.length);
      _rho[id] = 1.0/ArrayUtils.innerProduct(s,y);
      ++_k;
    }

    // the actual core of L-BFGS algo
    // compute new search direction using the ginfo at current beta and history
    protected  final double [] getSearchDirection(final double [] gradient, double [] q) {
      System.arraycopy(gradient,0,q,0,q.length);
      if(_k != 0) {
        int k = Math.min(_k,_m);
        for (int i = 1; i <= k; ++i) {
          int id = getId(-i);
          _alpha[id] = _rho[id] * ArrayUtils.innerProduct(_s[id], q);
          MathUtils.wadd(q, _y[id], -_alpha[id]);
        }
        int lastId = getId(-1);
        final double[] y = _y[lastId];
        double Hk0 = -1.0 / (ArrayUtils.innerProduct(y, y) * _rho[lastId]);
        ArrayUtils.mult(q, Hk0);
        for (int i = k; i > 0; --i) {
          int id = getId(-i);
          double beta = _rho[id] * ArrayUtils.innerProduct(_y[id], q);
          MathUtils.wadd(q, _s[id], -_alpha[id] - beta);
        }
      } else ArrayUtils.mult(q,-1);
      return q;
    }
  }

  /**
   * Solve the optimization problem defined by the user-supplied ginfo function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided ginfo function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The ginfo is likely to be the most expensive part and key for good perfomance.
   *
   * @param gslvr - user ginfo function
   * @param beta - starting solution
   * @return Optimal solution (coefficients) + ginfo info returned by the user ginfo
   * function evaluated at the found optmimum.
   */
  public final Result solve(GradientSolver gslvr, double [] beta, GradientInfo ginfo, ProgressMonitor pm) {
    if(_hist == null)
      _hist = new History(_historySz, beta.length);
    beta = beta.clone();
    int iter = 0;
    double rel_improvement = 1;
    final double [] pk = new double[beta.length];
    double minStep = 1e-12;
    double maxStep = 100;
    _lineSearch = new MoreThuente();
    while(!ArrayUtils.hasNaNsOrInfs(beta) && (pm.progress(beta, ginfo) && (ArrayUtils.linfnorm(ginfo._gradient,false) > _gradEps  && rel_improvement > _objEps) && iter != _maxIter)) {
      ++iter;
      _hist.getSearchDirection(ginfo._gradient,pk);
      if(!_lineSearch.evaluate(gslvr,ginfo,beta,pk,minStep,maxStep,20)) {
        break;
      }
      _lineSearch.setInitialStep(Math.max(10 * minStep, _lineSearch.step()));
      ArrayUtils.add(beta,ArrayUtils.mult(pk,_lineSearch.step()));
      GradientInfo newGinfo = _lineSearch.ginfo();
      _hist.update(pk, newGinfo._gradient, ginfo._gradient);
      rel_improvement = (ginfo._objVal - newGinfo._objVal)/ginfo._objVal;
      ginfo = newGinfo;
    }
    return new Result(ArrayUtils.linfnorm(ginfo._gradient,false) < _gradEps,iter,beta, ginfo);
  }

  /**
   * Solve the optimization problem defined by the user-supplied ginfo function using L-BFGS algorithm.
   *
   * Will result into multiple (10s to 100s or even 1000s) calls of the user-provided ginfo function.
   * Outside of that it does only limited single threaded computation (order of number of coefficients).
   * The ginfo is likely to be the most expensive part and key for good perfomance.
   *
   * @param gslvr  - user ginfo function
   * @params coefs - intial solution
   * @return Optimal solution (coefficients) + ginfo info returned by the user ginfo
   * function evaluated at the found optmimum.
   */
  public final Result solve(GradientSolver gslvr, double [] coefs){
    return solve(gslvr, coefs, gslvr.getGradient(coefs), new ProgressMonitor());
  }

  public static double [] startCoefs(int n, long seed){
    double [] res = MemoryManager.malloc8d(n);
    Random r = new Random(seed);
    for(int i = 0; i < res.length; ++i)
      res[i] = r.nextGaussian();
    return res;
  }
}
