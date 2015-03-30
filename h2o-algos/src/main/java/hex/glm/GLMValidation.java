package hex.glm;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.AUC2;
import water.Iced;
import water.Key;

/**
 * Class for GLMValidation.
 *
 * @author tomasnykodym
 *
 */
public class GLMValidation extends Iced {
  final double _ymu;
  double residual_deviance;
  double null_deviance;
  long nobs;
  AUC2 _auc;
  private AUC2.AUCBuilder _auc_bldr;
  double aic;// internal aic used only for poisson family!
  private double _aic2;// internal aic used only for poisson family!
  final Key dataKey;
  final GLMModel.GLMParameters _glm;
  final private int _rank;


  public GLMValidation(Key dataKey, double ymu, GLMParameters glm, int rank){
    _rank = rank;
    _ymu = ymu;
    _glm = glm;
    _auc_bldr = (glm._family == Family.binomial) ? new AUC2.AUCBuilder(AUC2.NBINS) : null;
    this.dataKey = dataKey;
  }

  public void add(double yreal, double eta, double ymodel){
    null_deviance += _glm.deviance(yreal, eta, _ymu);
    residual_deviance  += _glm.deviance(yreal, eta, ymodel);
    ++nobs;
    if( _auc_bldr != null ) _auc_bldr.perRow(ymodel, (int) yreal);

    if( _glm._family == Family.poisson ) { // aic for poisson
      long y = Math.round(yreal);
      double logfactorial = 0;
      for( long i = 2; i <= y; ++i )
        logfactorial += Math.log(i);
      _aic2 += (yreal * Math.log(ymodel) - logfactorial - ymodel);
    }
  }
  public void add(GLMValidation v){
    residual_deviance  += v.residual_deviance;
    null_deviance += v.null_deviance;
    nobs += v.nobs;
    _aic2 += v._aic2;
    if( _auc_bldr != null ) _auc_bldr.reduce(v._auc_bldr);
  }
  public final double nullDeviance(){return null_deviance;}
  public final double residualDeviance(){return residual_deviance;}
  public final long nullDOF(){return nobs-1;}
  public final long resDOF(){return nobs - _rank -1;}
  public double auc(){ return (_auc==null) ? Double.NaN : _auc._auc; }
  public double bestThreshold(){ return _auc==null ? Double.NaN : _auc.defaultThreshold();}
  public double aic(){return aic;}
  protected void computeAIC(){
    aic = 0;
    switch( _glm._family) {
      case gaussian:
        aic =  nobs * (Math.log(residual_deviance / nobs * 2 * Math.PI) + 1) + 2;
        break;
      case binomial:
        aic = residual_deviance;
        break;
      case poisson:
        aic = -2*_aic2;
        break; // aic is set during the validation task
      case gamma:
      case tweedie:
        aic = Double.NaN;
        break;
      default:
        assert false : "missing implementation for family " + _glm._family;
    }
    aic += 2*_rank;
  }

  protected void computeAUC(){
    if(_glm._family == Family.binomial)
      _auc = new AUC2(_auc_bldr);
  }
  @Override
  public String toString(){
    return "null_dev = " + null_deviance + ", res_dev = " + residual_deviance + ", auc = " + auc();
  }
}
