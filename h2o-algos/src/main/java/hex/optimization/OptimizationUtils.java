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
    public double [] _gradient;

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
    GradientInfo getGradient(double [] beta);
    GradientInfo getObjective(double [] beta);
  }


  public interface LineSearchSolver {
    boolean evaluate(double [] direction);
    double step();
    GradientInfo ginfo();
    LineSearchSolver setInitialStep(double s);
    int nfeval();
    double getObj();
    double[] getX();
  }

  public static final class SimpleBacktrackingLS implements LineSearchSolver {
    private double [] _beta;
    final double _stepDec = .33;
    private double _step;
    private final GradientSolver _gslvr;
    private GradientInfo _ginfo; // gradient info excluding l1 penalty
    private double _objVal; // objective including l1 penalty
    final double _l1pen;
    int _maxfev = 20;
    double _minStep = 1e-4;


    public SimpleBacktrackingLS(GradientSolver gslvr, double [] betaStart, double l1pen) {
      this(gslvr, betaStart, l1pen, gslvr.getObjective(betaStart));
    }
    public SimpleBacktrackingLS(GradientSolver gslvr, double [] betaStart, double l1pen, GradientInfo ginfo) {
      _gslvr = gslvr;
      _beta = betaStart;
      _ginfo = ginfo;
      _l1pen = l1pen;
      _objVal = _ginfo._objVal + _l1pen * ArrayUtils.l1norm(_beta,true);
    }
    public int nfeval() {return -1;}

    @Override
    public double getObj() {return _objVal;}

    @Override
    public double[] getX() {return _beta;}

    public LineSearchSolver setInitialStep(double s){
      return this;
    }

    @Override
    public boolean evaluate(double[] direction) {
      double step = 1;
      double minStep = 1;
      for(double d:direction) {
        d = Math.abs(1e-4/d);
        if(d < minStep) minStep = d;
      }
      double [] newBeta = direction.clone();
      for(int i = 0; i < _maxfev && step >= minStep; ++i, step*= _stepDec) {
        GradientInfo ginfo = _gslvr.getObjective(ArrayUtils.wadd(_beta,direction,newBeta,step));
        double objVal = ginfo._objVal + _l1pen * ArrayUtils.l1norm(newBeta,true);
        if(objVal < _objVal){
          _ginfo = ginfo;
          _objVal = objVal;
          _beta = newBeta;
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

    @Override public String toString(){return "";}
  }



  public static final class MoreThuente implements LineSearchSolver {
    double _stMin, _stMax;

    double _initialStep = 1;
    double _minRelativeImprovement = 1e-8;
    private final GradientSolver _gslvr;
    private double [] _beta;


    public MoreThuente(GradientSolver gslvr, double [] betaStart){
      this(gslvr,betaStart,gslvr.getGradient(betaStart),.1,.1,1e-2);
    }
    public MoreThuente(GradientSolver gslvr, double [] betaStart, GradientInfo ginfo){
      this(gslvr,betaStart,ginfo,.1,.1,1e-2);
    }
    public MoreThuente(GradientSolver gslvr, double [] betaStart, GradientInfo ginfo, double ftol, double gtol, double xtol){
      _gslvr = gslvr;
      _beta = betaStart;
      _ginfox = ginfo;
      if(ginfo._gradient == null)
        throw new IllegalArgumentException("GradientInfo for MoreThuente line search solver must include gradient");
      _ftol = ftol;
      _gtol = gtol;
      _xtol = xtol;
    }

    public MoreThuente setInitialStep(double t) {_initialStep = t; return this;}

    @Override
    public int nfeval() {
      return _iter;
    }

    @Override
    public double getObj() {return ginfo()._objVal;}

    @Override
    public double[] getX() { return _beta;}

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

    int _maxfev = 20;
    double _maxStep = 1e10;
    double _minStep = 1e-10;
    @Override
    public boolean evaluate(double [] direction) {
      double oldObjval = _ginfox._objVal;
      double step = _initialStep;
      _bound = false;
      _brackt = false;
      _stx = _sty = 0;
      _stMin = _stMax = 0;
      _betGradient = null;
      _bestPsiVal = Double.POSITIVE_INFINITY;
      _bestStep = 0;
      double maxObj = _ginfox._objVal - _minRelativeImprovement*_ginfox._objVal;
      final double dgInit = ArrayUtils.innerProduct(_ginfox._gradient, direction);
      final double dgtest = dgInit * _ftol;
      assert dgtest < 1e-4:"invalid gradient/direction, got positive differential " + dgtest;
      if(dgtest >= 0)
        return false;
      double [] beta = new double[_beta.length];
      double width = _maxStep - _minStep;
      double oldWidth = 2*width;
      boolean stage1 = true;
      _fvx = _fvy = _ginfox._objVal;
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
        step = Math.min(step,_maxStep);
        step = Math.max(step,_minStep);
        double maxFval = oldObjval + step * dgtest;

        for (int i = 0; i < beta.length; ++i)
          beta[i] = _beta[i] + step * direction[i];
        GradientInfo newGinfo = _gslvr.getGradient(beta);
        if(newGinfo._objVal < maxObj && (_betGradient == null || (newGinfo._objVal - maxFval) < _bestPsiVal)){
          _bestPsiVal = (newGinfo._objVal - maxFval);
          _betGradient = newGinfo;
          _bestStep = step;
        }
        ++_iter;
        if(_iter < _maxfev && (!Double.isNaN(step) && (Double.isNaN(newGinfo._objVal) || Double.isInfinite(newGinfo._objVal) || ArrayUtils.hasNaNsOrInfs(newGinfo._gradient)))) {
          _brackt = true;
          _sty = step;
          _maxStep = step;
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
        if (step == _maxStep && newGinfo._objVal <= maxFval & dgp <= dgtest){
          _returnStatus = 5;
          _stx = step;
          _ginfox = newGinfo;
          break;
        }
        if (step == _minStep && (newGinfo._objVal > maxFval | dgp >= dgtest)){
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
        if (_iter >= _maxfev){
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
      boolean succ = _ginfox._objVal < oldObjval;
      if(succ) _beta = beta;
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
