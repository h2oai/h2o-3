package hex.glm;


import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.ConfusionMatrix2;
import hex.AUC;
import water.Iced;
import water.Key;
import water.util.ModelUtils;

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
  float best_threshold;
  double auc = Double.NaN;
  Key [] xval_models;
  double aic;// internal aic used only for poisson family!
  private double _aic2;// internal aic used only for poisson family!
  final Key dataKey;
  public final float [] thresholds;
  ConfusionMatrix2[] _cms;
  final GLMModel.GLMParameters _glm;
  final private int _rank;

  public static class GLMXValidation extends GLMValidation {
    public GLMXValidation(GLMModel mainModel, GLMModel [] xvalModels, GLMValidation [] xvals, double lambda, long nobs, float [] thresholds) {
      super(mainModel._key, mainModel._ymu, mainModel._parms, mainModel.rank(lambda),thresholds);
      xval_models = new Key[xvalModels.length];
      for(int i = 0; i < xval_models.length; ++i)
        xval_models[i] = xvalModels[i]._key;
      double t = 0;
      for(int i = 0; i < xvalModels.length; ++i){
        add(xvals[i]);
        t += xvals[i].best_threshold;
      }
      computeAUC();
      computeAIC();
      best_threshold = (float)(t/xvalModels.length);
      this.nobs = nobs;
    }
  }
  public GLMValidation(Key dataKey, double ymu, GLMModel.GLMParameters glm, int rank){
    this(dataKey, ymu, glm, rank,glm.family == Family.binomial?ModelUtils.DEFAULT_THRESHOLDS:null);
  }
  public GLMValidation(Key dataKey, double ymu, GLMParameters glm, int rank, float [] thresholds){
    _rank = rank;
    _ymu = ymu;
    _glm = glm;
    if(_glm.family == Family.binomial){
      _cms = new ConfusionMatrix2[thresholds.length];
      for(int i = 0; i < _cms.length; ++i)
        _cms[i] = new ConfusionMatrix2(2);
    }
    this.dataKey = dataKey;
    this.thresholds = thresholds;
  }

  public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make());}
  public void add(double yreal, double ymodel){
    null_deviance += _glm.deviance(yreal, _ymu);
    if(_glm.family == Family.binomial) // classification -> update confusion matrix too
      for(int i = 0; i < thresholds.length; ++i)
        _cms[i].add((int)yreal, (ymodel >= thresholds[i])?1:0);
    residual_deviance  += _glm.deviance(yreal, ymodel);
    ++nobs;

    if( _glm.family == Family.poisson ) { // aic for poisson
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
    if(_cms == null)_cms = v._cms;
    else for(int i = 0; i < _cms.length; ++i)_cms[i].add(v._cms[i]);
  }
  public final double nullDeviance(){return null_deviance;}
  public final double residualDeviance(){return residual_deviance;}
  public final long nullDOF(){return nobs-1;}
  public final long resDOF(){return nobs - _rank -1;}
  public double auc(){return auc;}
  public double aic(){return aic;}
  protected void computeAIC(){
    aic = 0;
    switch( _glm.family ) {
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
        assert false : "missing implementation for family " + _glm.family;
    }
    aic += 2*_rank;
  }

  protected void computeAUC(){
    if(_glm.family == Family.binomial){
      for(ConfusionMatrix2 cm:_cms)cm.reComputeErrors();
      AUC auc = new AUC(_cms,thresholds,/*TODO: add CM domain*/null);
      this.auc = auc.data().AUC();
      best_threshold = auc.data().threshold();
    }
  }
  @Override
  public String toString(){
    return "null_dev = " + null_deviance + ", res_dev = " + residual_deviance + ", auc = " + auc();
  }



  /**
   * Computes area under the ROC curve. The ROC curve is computed from the confusion matrices
   * (there is one for each computed threshold). Area under this curve is then computed as a sum
   * of areas of trapezoids formed by each neighboring points.
   *
   * @return estimate of the area under ROC curve of this classifier.
   */
  double[] tprs;
  double[] fprs;

  private double trapeziod_area(double x1, double x2, double y1, double y2) {
    double base = Math.abs(x1 - x2);
    double havg = 0.5 * (y1 + y2);
    return base * havg;
  }

}
