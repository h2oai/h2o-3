package hex.optimization;


import water.Iced;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * Created by tomasnykodym on 9/29/15.
 */
public class OptimizationUtils {

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
   *  Provides ginfo computation and line search evaluation specific to given problem.
   *  Typically just a wrapper around MRTask calls.
   */
  public interface GradientSolver {
    /**
     * Evaluate ginfo at solution beta.
     * @param beta
     * @return
     */
    public abstract GradientInfo  getGradient(double [] beta);
  }


  public interface LineSearchSolver {
    public boolean evaluate(GradientSolver slvr, GradientInfo ginfo, double [] betaStart, double [] direction, final double minStep, final double maxStep, int maxfev);
    public double step();
    public GradientInfo ginfo();
    public LineSearchSolver setInitialStep(double s);
    public int nfeval();
  }

  public static final class BacktrackingLS implements LineSearchSolver {
    double [] _beta;
    final double _stepDec;
    final double _ftol = .1;
    final double _gtol = .9;
    double _step;

    public BacktrackingLS(double stepDec) {_stepDec = stepDec;}
    public int nfeval() {return -1;}

    public LineSearchSolver setInitialStep(double s){
      return this;
    }
    GradientInfo _ginfo;
    @Override
    public boolean evaluate(GradientSolver slvr, GradientInfo ginfo, double[] betaStart, double[] direction, double minStep, double maxStep, int maxfev) {
      if(_beta == null)
        _beta = new double[betaStart.length];
      double dg = ArrayUtils.innerProduct(ginfo._gradient,direction);
      double fhat = _ftol*dg;
      double ghat = -_gtol*dg;
      assert fhat < 0;
      for(double step = maxStep; step >= minStep && --maxfev >= 0; step *= _stepDec) {
        for(int i = 0; i < betaStart.length; ++i)
          _beta[i] = betaStart[i] + step * direction[i];
        GradientInfo newGinfo = slvr.getGradient(_beta);
        double dgp = Math.abs(ArrayUtils.innerProduct(newGinfo._gradient,direction));
        if(newGinfo._objVal < ginfo._objVal + step*fhat && Math.abs(ArrayUtils.innerProduct(newGinfo._gradient,direction)) < ghat) {
          _ginfo = newGinfo;
          _step = step;
          return true;
        }
      }
      return false;
    }

    @Override
    public double step() {
      return _step;
    }

    @Override
    public GradientInfo ginfo() {
      return _ginfo;
    }

  }
  public static final class MoreThuente implements LineSearchSolver {
    double _stMin, _stMax;

    double _initialStep = 1;
    double _minRelativeImprovement = 1e-8;

    public MoreThuente(){}
    public MoreThuente(double ftol, double gtol, double xtol) {
      _ftol = ftol;
      _gtol = gtol;
      _xtol = xtol;
    }

    public MoreThuente setXtol(double xtol) {
      _xtol = xtol;
      return this;
    }

    public MoreThuente setFtol(double ftol) {
      _ftol = ftol;
      return this;
    }

    public MoreThuente setGtol(double gtol) {
      _gtol = gtol;
      return this;
    }

    public MoreThuente setInitialStep(double t) {_initialStep = t; return this;}

    @Override
    public int nfeval() {
      return _iter;
    }

    double _xtol = 1e-2;
    double _ftol = .1; // .2/.25 works
    double _gtol = .1;
    double _xtrapf = 4;

    // fval, dg and step of the best step so far
    double _fvx;
    double _dgx;
    double _stx;

    double _bestStep;
    GradientInfo _betGradient; // gradient info with at least minimal relative improvement and best value of augmented function
    double  _bestPsiVal; // best value of augmented function
    GradientInfo _ginfox;

    // fval, dg and step of the best step so far
    double _fvy;
    double _dgy;
    double _sty;

    boolean _brackt;
    boolean _bound;

    int _returnStatus;

    public final String [] messages = new String[]{
      "In progress or not evaluated", // 0
      "The sufficient decrease condition and the directional derivative condition hold.", // 1
      "Relative width of the interval of uncertainty is at most xtol.", // 2
      "Number of calls to gradient solver has reached the limit.", // 3
      "The step is at the lower bound stpmin.", // 4
      "The step is at the upper bound stpmax.", // 5
      "Rounding errors prevent further progress, ftol/gtol tolerances may be too small." // 6
    };

    private double nextStep(GradientInfo ginfo, double dg, double stp, double off) {
      double fvp = ginfo._objVal - stp*off;
      double dgp = dg - off;
      double fvx = _fvx - _stx * off;
      double fvy = _fvy - _sty * off;
      double stx = _stx;
      double sty = _sty;
      double dgx = _dgx - off;
      double dgy = _dgy - off;

      if ((_brackt && (stp <= Math.min(stx,sty) || stp >= Math.max(stx,sty))) || dgx*(stp-stx) >= 0.0)
        return Double.NaN;
      double theta = 3 * (fvx - fvp) / (stp - stx) + dgx + dgp;
      double s = Math.max(Math.max(Math.abs(theta),Math.abs(dgx)),Math.abs(dgp));
      double sInv = 1/s;
      double ts = theta*sInv;
      double gamma = s*Math.sqrt(Math.max(0., (ts*ts) - ((dgx * sInv) * (dgp*sInv))));

      int info = 0;
      // case 1
      double nextStep;
      if (fvp > fvx) {
        info = 1;
        if (stp < stx) gamma = -gamma;
        _bound = true;
        _brackt = true;
        double p = (gamma - dgx) + theta;
        double q = ((gamma - dgx) + gamma) + dgp;
        double r = p / q;
        double stpc = stx + r * (stp - stx);
        double stpq = stx + ((dgx / ((fvx - fvp) / (stp - stx) + dgx)) / 2) * (stp - stx);
        nextStep = (Math.abs(stpc - stx) < Math.abs(stpq - stx)) ? stpc : stpc + (stpq - stpc) / 2;
      } else  if (dgp * dgx  < 0) { // case 2
        info = 2;
        if (stp > stx) gamma = -gamma;
        _bound = false;
        _brackt = true;
        double p = (gamma - dgp) + theta;
        double q = ((gamma - dgp) + gamma) + dgx;
        double r = p / q;
        double stpc = stp + r * (stx - stp);
        double stpq = stp + (dgp / (dgp - dgx)) * (stx - stp);
        nextStep = (Math.abs(stpc - stp) > Math.abs(stpq - stp)) ? stpc : stpq;
      } else if (Math.abs(dgp) < Math.abs(dgx)) { // case 3
        info = 3;
        if (stp > stx) gamma = -gamma;
        _bound = true;
        double p = gamma - dgp + theta;
        double q = gamma + dgx - dgp + gamma;
        double r = p / q;
        double stpc;
        if (r < 0.0 && gamma != 0.0)
          stpc = stp + r * (stx - stp);
        else if (stp > stx)
          stpc = _stMax;
        else
          stpc = _stMin;
        // stpq = stp + (dp/(dp-dx))*(stx - stp);
        double stpq = stp + (dgp / (dgp - dgx)) * (stx - stp);
        if (_brackt)
          nextStep = (Math.abs(stp - stpc) < Math.abs(stp - stpq)) ? stpc : stpq;
        else
          nextStep = (Math.abs(stp - stpc) > Math.abs(stp - stpq)) ? stpc : stpq;
      } else {
        // case 4
        info = 4;
        _bound = false;
        if (_brackt) {
          theta = 3 * (fvp - fvy) / (sty - stp) + dgy + dgp;
          gamma = Math.sqrt(theta * theta - dgy * dgp);
          if (stp > sty) gamma = -gamma;
          double p = (gamma - dgp) + theta;
          double q = ((gamma - dgp) + gamma) + dgy;
          double r = p / q;
          nextStep = stp + r * (sty - stp);
        } else
          nextStep = stp > stx ? _stMax : _stMin;
      }

      if(fvp > fvx) {
        _sty = stp;
        _fvy = ginfo._objVal;
        _dgy = dg;
      } else {
        if(dgp * dgx < 0) {
          _sty = _stx;
          _fvy = _fvx;
          _dgy = _dgx;
        }
        _stx = stp;
        _fvx = ginfo._objVal;
        _dgx = dg;
        _ginfox = ginfo;
      }
      if(nextStep > _stMax)
        nextStep = _stMax;
      if(nextStep < _stMin)
        nextStep = _stMin;
      if (_brackt & _bound)
        if (_sty > _stx)
          nextStep = Math.min(_stx + .66 * (_sty - _stx), nextStep);
        else
          nextStep = Math.max(_stx + .66 * (_sty - _stx), nextStep);
      return nextStep;
    }

    public String toString(){
      return "MoreThuente line search, iter = " + _iter + ", status = " + messages[_returnStatus] + ", step = " + _stx + ", I = " + "[" + _stMin + ", " + _stMax + "], grad = " + _dgx + ", bestObj = "  + _fvx;
    }
    private int _iter;
    private double [] _beta;
    public boolean evaluate(GradientSolver slvr, final GradientInfo ginfo, double [] betaStart, double [] direction, final double minStep, double maxStep, int maxfev) {
      double step = _initialStep;
      _bound = false;
      _brackt = false;
      _stx = _sty = 0;
      _stMin = _stMax = 0;
      _betGradient = null;
      _bestPsiVal = Double.POSITIVE_INFINITY;
      _bestStep = 0;
      double maxObj = ginfo._objVal - _minRelativeImprovement*ginfo._objVal;
      final double dgInit = ArrayUtils.innerProduct(ginfo._gradient, direction);
      final double dgtest = dgInit * _ftol;
      if(dgtest >= 0) return false;
//      assert dgtest < 0:"invalid gradient/direction, got positive differential " + dgtest;
      if(_beta == null)_beta = new double[betaStart.length];
      double width = maxStep - minStep;
      double oldWidth = 2*width;

      boolean stage1 = true;
      _ginfox = ginfo;
      _fvx = _fvy = ginfo._objVal;
      _dgx = _dgy = dgInit;
      _iter = 0;

      while (true) {
        if (_brackt) {
          _stMin = Math.min(_stx, _sty);
          _stMax = Math.max(_stx, _sty);
        } else {
          _stMin = _stx;
          _stMax = step + _xtrapf * (step - _stx);
        }

        step = Math.min(step,maxStep);
        step = Math.max(step,minStep);
        double maxFval = ginfo._objVal + step * dgtest;

        for (int i = 0; i < _beta.length; ++i)
          _beta[i] = betaStart[i] + step * direction[i];
        GradientInfo newGinfo = slvr.getGradient(_beta);
        if(newGinfo._objVal < maxObj && (_betGradient == null || (newGinfo._objVal - maxFval) < _bestPsiVal)){
          _bestPsiVal = (newGinfo._objVal - maxFval);
          _betGradient = newGinfo;
          _bestStep = step;
        }
        ++_iter;
        if(!Double.isNaN(step) && (Double.isNaN(newGinfo._objVal) || Double.isInfinite(newGinfo._objVal) || ArrayUtils.hasNaNsOrInfs(newGinfo._gradient))) {
          _brackt = true;
          _sty = step;
          maxStep = step;
          _fvy = Double.POSITIVE_INFINITY;
          _dgy = Double.MAX_VALUE;
          step *= .5;
          continue;
        }

        double dgp = ArrayUtils.innerProduct(newGinfo._gradient, direction);
        if(Double.isNaN(step) || _brackt && (step <= _stMin || step >= _stMax)) {
          _returnStatus = 6;
          break;
        }
        if (step == maxStep && newGinfo._objVal <= maxFval & dgp <= dgtest){
          _returnStatus = 5;
          _stx = step;
          _ginfox = newGinfo;
          break;
        }
        if (step == minStep && (newGinfo._objVal > maxFval | dgp >= dgtest)){
          _returnStatus = 4;
          if(_betGradient != null) {
             _stx = _bestStep;
             _ginfox = _betGradient;
          } else {
            _stx = step;
            _ginfox = newGinfo;
          }
          break;
        }
        if (_iter >= maxfev){
          _returnStatus = 3;
          if(_betGradient != null) {
            _stx = _bestStep;
            _ginfox = _betGradient;
          } else {
            _stx = step;
            _ginfox = newGinfo;
          }
          break;
        }
        if (_brackt && (_stMax-_stMin) <= _xtol*_stMax) {
          _ginfox = newGinfo;
          _returnStatus = 2;
          break;
        }
        // check for convergence
        if (newGinfo._objVal < maxFval && Math.abs(dgp) <= -_gtol * dgInit) { // got solution satisfying both conditions
          _stx = step;
          _dgx = dgp;
          _fvx = newGinfo._objVal;
          _ginfox = newGinfo;
          _returnStatus = 1;
          break;
        }
        // f > ftest1 || dg < min(ftol,gtol)*dginit
        stage1 = stage1 && (newGinfo._objVal > maxFval || dgp < dgtest);
        boolean useAugmentedFuntcion = stage1 && newGinfo._objVal <= _fvx && newGinfo._objVal > maxFval;
        double off = useAugmentedFuntcion?dgtest:0;
        double nextStep = nextStep(newGinfo,dgp,step,off);
        if (_brackt) {
          if (Math.abs(_sty - _stx) >= .66 * oldWidth)
            nextStep = _stx + .5 * (_sty - _stx);
          oldWidth = width;
          width = Math.abs(_sty - _stx);
        }
        step = nextStep;
      }
      boolean succ = _ginfox._objVal < ginfo._objVal;
      return succ;
    }

    @Override
    public double step() {return _stx;}


    @Override
    public GradientInfo ginfo() {
      return _ginfox;
    }
  }

}
