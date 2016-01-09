package ai.h2o.automl.tasks;

import hex.Model;
import hex.ModelMetricsSupervised;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.util.ArrayUtils;

/**
 *  Collect the Variance Inflation Factors for numerical columns.
 *
 *  Useful for column selection strategies and detecting multicollinearity.
 *
 *  Given a set of predictors, each numerical column is regressed on remaining predictors,
 *  and the R^2 value is inspected. R^2 of ~ 0.875 is indicative of collinear columns.
 *
 *  Typically a DS would possibly drop out predictors with large (>10) VIFs using some kind
 *  of contextual clue about how columns may be correlated based on data collection methods
 *  or feature generation. AutoML may not have this same information, but it can pit models
 *  against each other built on data with various columns dropped out.
 */
public class VIF extends DTask<VIF> {

  private GLM _glm;
  private double _vif;

  private VIF(Key<Frame> trainFrame, String response, String[] include, String[] colNames) {
    GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gaussian);
    params._train = trainFrame;
    params._lambda = new double[]{0};
    params._standardize=false;
    params._ignored_columns = ArrayUtils.difference(colNames, include);
    params._response_column = response;
    _glm = new GLM(params);
  }

  public static VIF[] make(Key<Frame> trainFrame, String[] predictors, String[] colNames) {
    VIF[] vifs = new VIF[predictors.length];
    for(int i=0;i<vifs.length;++i)
      vifs[i] = new VIF(trainFrame, predictors[i], predictors, colNames);
    return vifs;
  }

  @Override public void compute2() { _glm.trainModel(); tryComplete(); }

  double vif() {
    if( _glm!=null ) {
      Model m;
      _vif = 1. / (1. - ((ModelMetricsSupervised) (m=_glm.get())._output._training_metrics).r2());
      m.delete();
      _glm=null;
    }
    return _vif;
  }

  // drop the item at index i and return the shortened String[]
  private static String[] dropElem(String[] a, int i) {
    String[] b = new String[a.length-1];
    int bidx=0;
    for(int j=0;j<a.length;++j)
      if( j!=i ) b[bidx++]=a[j];
    return b;
  }

  // blocking call for launching and collecting the VIFs
  public static void launchVIFs(VIF[] vifs) {
    Futures fs = new Futures();
    for( int i=0; i<vifs.length; i++ )
      fs.add(RPC.call(H2O.CLOUD._memary[i%H2O.getCloudSize()], vifs[i]));
    fs.blockForPending();
  }
}
