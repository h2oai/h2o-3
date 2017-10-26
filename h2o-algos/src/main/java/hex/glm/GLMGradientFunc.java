package hex.glm;

import hex.DataInfo;
import hex.optimization.OptimizationUtils;
import water.Job;
import water.util.ArrayUtils;

/**
 * Gradient and objective value computation for GLM.
 * NOTE: gradient contains only parts of the objective which have gradient defined - i.e. only likelihood + l2pen
 */
public final class GLMGradientFunc extends OptimizationUtils.GradientFunc<GLM.GLMGradientInfo> {
  final GLMModel.GLMParameters _parms;
  final DataInfo _dinfo;
  final BetaConstraint _bc;
  final double _l1pen;
  final double _l2pen; // l2 penalty
  final Job _job;
  private final double _obj_reg;

  public GLMGradientFunc(Job job, double obj_reg, GLMModel.GLMParameters glmp, DataInfo dinfo, double alpha, double lambda, BetaConstraint bc) {
    _job = job;
    _bc = bc;
    _parms = glmp;
    _dinfo = dinfo;
    _l1pen = alpha*lambda;
    _l2pen = (1-alpha)*lambda;
    _obj_reg = obj_reg;
  }

  public GLM.GLMGradientInfo adjustL2Gradient(double lambda, double [] beta, GLM.GLMGradientInfo ginfo){
    int P = _dinfo.fullN();
    return new GLM.GLMGradientInfo(ginfo._likelihood, ginfo._objVal + .5*lambda*ArrayUtils.l2norm2(beta,P), ArrayUtils.add_l2_grad(ginfo._gradient.clone(),beta,lambda,P));
  }
  @Override
  public GLM.GLMGradientInfo getGradient(double[] beta) {
      assert beta.length == _dinfo.fullN() + 1:"beta.length = " + beta.length + ", dinfo.fullN + 1 = " + (_dinfo.fullN()+1);
      assert _parms._intercept || (beta[beta.length-1] == 0);
    double [] gradient;
    double  likelihood;
    if(_parms._family == GLM.Family.multinomial){
      GLMTask.GLMMultinomialGradientTask gt = new GLMTask.GLMMultinomialGradientTask(_job,_dinfo,beta);
      gradient = gt.gradient();
      likelihood = gt._likelihood;
    } else {
      GLMTask.GLMGradientTask gt;
      if (_parms._family == GLM.Family.binomial && _parms._link == GLM.Link.logit)
        gt = new GLMTask.GLMBinomialGradientTask(_job == null ? null : _job._key, _obj_reg, _dinfo, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
      else if (_parms._family == GLM.Family.gaussian && _parms._link == GLM.Link.identity)
        gt = new GLMTask.GLMGaussianGradientTask(_job == null ? null : _job._key, _dinfo, _obj_reg, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
      else if (_parms._family == GLM.Family.poisson && _parms._link == GLM.Link.log)
        gt = new GLMTask.GLMPoissonGradientTask(_job == null ? null : _job._key, _dinfo, _obj_reg, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
      else if (_parms._family == GLM.Family.quasibinomial)
        gt = new GLMTask.GLMQuasiBinomialGradientTask(_job == null ? null : _job._key, _dinfo, _obj_reg, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
      else
        gt = new GLMTask.GLMGenericGradientTask(_job == null ? null : _job._key, _dinfo, _obj_reg, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
      gradient = gt._gradient;
      likelihood = gt._likelihood;
    }
    if (!_parms._intercept) { // no intercept, null the gradient
      int P = _dinfo.fullN();
      for (int i = P; i < gradient.length; i += P)
        gradient[P] = 0;
    }
    double obj = likelihood * _obj_reg  + ((_l2pen != 0)?.5 * _l2pen * ArrayUtils.l2norm2(beta, _dinfo.fullN()):0);
    if (_bc != null && _bc._betaGiven != null && _bc._rho != null)
      obj = L_BFGS_Solver.ProximalGradientFunc.proximal_gradient(gradient, obj, beta, _bc._betaGiven, _bc._rho);
    return new GLM.GLMGradientInfo(likelihood, obj, gradient);
  }
  @Override
  public GLM.GLMGradientInfo getObjective(double[] beta) {
    double l = new GLMTask.GLMResDevTask(_job._key,_dinfo,_parms,beta).doAll(_dinfo._adaptedFrame)._likelihood;
    double obj = l*_obj_reg + _l1pen*ArrayUtils.l1norm(beta,_dinfo.fullN()) + .5*_l2pen*ArrayUtils.l2norm2(beta,_dinfo.fullN());
    if (_bc != null && _bc._betaGiven != null && _bc._rho != null)
      obj = L_BFGS_Solver.ProximalGradientFunc.proximal_gradient(new double[beta.length], obj, beta, _bc._betaGiven, _bc._rho);
    return new GLM.GLMGradientInfo(l,obj,null);
  }
}
