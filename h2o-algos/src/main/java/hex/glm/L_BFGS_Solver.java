package hex.glm;

import hex.optimization.ADMM;
import hex.optimization.L_BFGS;
import hex.optimization.OptimizationUtils;
import water.H2O;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 * Created by tomas on 7/13/17.
 */
class L_BFGS_Solver extends GLMSolver {

  @Override
  protected GLM.GLMState fit(final GLM.GLMState state) {
    double [] beta = state.beta();
    final double l1pen = state.l1pen();
    final OptimizationUtils.GradientFunc<GLM.GLMGradientInfo> gslvr = state.gradientFunc();
    if (beta == null) throw H2O.unimpl();
    L_BFGS lbfgs = new L_BFGS().setObjEps(state.objectiveEpsilon()).setGradEps(state.gradientEpsilon()).setMaxIter(state.maxIterations());
    int P = state.activeData().fullN();
    if (l1pen > 0 || state.activeBC().hasBounds()) {
      // need ADMM solver wrapped around L_BFGS solver
      // first estimate rho
      GLM.GLMGradientInfo ginfo = state.nullGradient();
      double[] direction = ArrayUtils.mult(state.activeData().selectActive(ginfo._gradient.clone()), -1);
      double t = 1;
      if (l1pen > 0) {
        OptimizationUtils.MoreThuente mt = new OptimizationUtils.MoreThuente(gslvr,new double[direction.length]);
        mt.evaluate(direction);
        t = mt.step();
      }
      double[] rho = MemoryManager.malloc8d(beta.length);
      double r = state.activeBC().hasBounds()?1:.1;
      BetaConstraint bc = state.activeBC();
      for (int i = 0; i < rho.length - 1; ++i)
        rho[i] = r * ADMM.L1Solver.estimateRho(t * direction[i], l1pen, bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[i], bc._betaUB == null ? Double.POSITIVE_INFINITY : bc._betaUB[i]);
      for (int ii = P; ii < rho.length; ii += P + 1)
        rho[ii] = r * ADMM.L1Solver.estimateRho(t * direction[ii], 0, bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[ii], bc._betaUB == null ? Double.POSITIVE_INFINITY : bc._betaUB[ii]);
      final double[] objvals = new double[2];
      objvals[1] = Double.POSITIVE_INFINITY;
      double reltol = ADMM.L1Solver.DEFAULT_RELTOL;
      double abstol = ADMM.L1Solver.DEFAULT_ABSTOL;
      double ADMM_gradEps = 1e-3;
      ProximalGradientFunc innerSolver = new ProximalGradientFunc(gslvr, beta.clone(), rho, state.maxIterations(), state.objectiveEpsilon() * 1e-1, state.gradientEpsilon(), state.gradient());
      ADMM.L1Solver l1Solver = new ADMM.L1Solver(ADMM_gradEps, 250, reltol, abstol, null);
      l1Solver._pm = new ADMM.L1Solver.ProgressMonitor(){
        @Override public void progress(double [] beta, int iter){
          state.update(beta,gslvr.getObjective(beta)._likelihood,iter);
        }
      };
      l1Solver.solve(innerSolver, beta, l1pen, true, state.activeBC()._betaLB, state.activeBC()._betaUB);
      state.update(beta,gslvr.getGradient(beta),innerSolver.iter());
      return state;
    } else {
      L_BFGS.Result r = lbfgs.solve(gslvr, beta, state.gradient(), new L_BFGS.ProgressMonitor() {
        @Override
        public boolean progress(double[] beta, OptimizationUtils.GradientInfo ginfo, int iter) {
          if(iter < 4 || (iter & 3) == 0) {
            Log.info(state.LogMsg("LBFGS, gradient norm = " + ArrayUtils.linfnorm(ginfo._gradient, false)));
            return state.update(beta, (GLM.GLMGradientInfo)ginfo, iter);
          } else return true;
        }
      });
      Log.info(state.LogMsg(r.toString()));
      state.update(r.coefs,(GLM.GLMGradientInfo)r.ginfo,0);
      return state;
    }
  }

  /**
   * Simple wrapper around ginfo computation, adding proximal penalty
   */
  public static class ProximalGradientFunc extends OptimizationUtils.GradientFunc implements ADMM.ProximalSolver {
    final OptimizationUtils.GradientFunc _solver;
    double[] _betaGiven;
    double[] _beta;
    private ADMM.ProximalGradientInfo _ginfo;
    final double[] _rho;
    private final double _objEps;
    private final double _gradEps;
    final int _maxIter;

    public ProximalGradientFunc(OptimizationUtils.GradientFunc s, double[] betaStart, double[] rho, int maxIter, double objEps, double gradEps, OptimizationUtils.GradientInfo ginfo) {
      super();
      _solver = s;
      _rho = rho;
      _maxIter = maxIter;
      _objEps = objEps;
      _gradEps = gradEps;
      _beta = betaStart;
      _betaGiven = MemoryManager.malloc8d(betaStart.length);
  //      _ginfo = new ProximalGradientInfo(ginfo,ginfo._objVal,ginfo._gradient);
    }

    public static double proximal_gradient(double[] grad, double obj, double[] beta, double[] beta_given, double[] rho) {
      for (int i = 0; i < beta.length; ++i) {
        double diff = (beta[i] - beta_given[i]);
        double pen = rho[i] * diff;
        if(grad != null)
          grad[i] += pen;
        obj += .5 * pen * diff;
      }
      return obj;
    }

    private ADMM.ProximalGradientInfo computeProxGrad(OptimizationUtils.GradientInfo ginfo, double [] beta) {
      assert !(ginfo instanceof ADMM.ProximalGradientInfo);
      double[] gradient = ginfo._gradient.clone();
      double obj = proximal_gradient(gradient, ginfo._objVal, beta, _betaGiven, _rho);
      return new ADMM.ProximalGradientInfo(ginfo, obj, gradient);
    }
    @Override
    public ADMM.ProximalGradientInfo getGradient(double[] beta) {
      return computeProxGrad(_solver.getGradient(beta),beta);
    }

    @Override
    public OptimizationUtils.GradientInfo getObjective(double[] beta) {
      return null;
    }

    @Override
    public double[] rho() {
      return _rho;
    }

    private int _iter;

    @Override
    public boolean solve(double[] beta_given, double[] beta) {
      if(_iter >= _maxIter) {
        Log.warn("ProximalGradientFunc reached max number of iterations of " + _maxIter);
        return false;
      }
      OptimizationUtils.GradientInfo origGinfo = (_ginfo == null || !Arrays.equals(_beta,beta))
          ?_solver.getGradient(beta)
          :_ginfo._origGinfo;
      System.arraycopy(beta_given,0,_betaGiven,0,beta_given.length);
      L_BFGS.Result r = new L_BFGS().setObjEps(_objEps).setGradEps(_gradEps).solve(this, beta, _ginfo = computeProxGrad(origGinfo,beta), null);
      System.arraycopy(r.coefs,0,beta,0,r.coefs.length);
      _beta = r.coefs;
      _iter += r.iter;
      _ginfo = (ADMM.ProximalGradientInfo) r.ginfo;
      return r.converged;
    }

    @Override
    public boolean hasGradient() {
      return true;
    }

    @Override
    public OptimizationUtils.GradientInfo gradient(double[] beta) {
      return getGradient(beta)._origGinfo;
    }

    @Override
    public int iter() {
      return _iter;
    }
  }
}
