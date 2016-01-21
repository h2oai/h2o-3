package ai.h2o.automl.tasks;

import water.MRTask;
import water.fvec.Vec;
import water.util.ArrayUtils;

/**
 *  Compute Dummy Scores for a given target column.
 */
abstract class DummyScore extends MRTask<DummyScore> {
  protected double _wcount;
  protected final FCN[] _losses;
  // out
  protected double[/*losses*/][/*dummy values*/] _dummies; // the full set of dummy losses
  protected DummyScore(String[] losses) {
    _losses = new FCN[losses==null?1:losses.length];
    if( losses==null ) _losses[0] = FCN.valueOf("mse");  // default to mse
    else {
      int i=0;
      for (String loss : losses) _losses[i++] = FCN.valueOf(loss);
    }
    _dummies = new double[_losses.length][];
  }

  // TODO: allow user to specify some custom loss
  enum FCN {
    mse() {   // AKA deviance for regression and guassian family
      @Override void op( double[] d0s, double err, double weight, int which) { d0s[which] += weight*err*err; }
      @Override void postOp( double ds[], double wcount ) { ArrayUtils.div(ds,wcount); }
      @Override double classificationConstScore(Vec y, int cls) { return 1. - ((double)y.bins()[cls] / (double)(y.length() - y.naCnt())); }
    },
    logloss() {
      double _eps=1e-15;
      @Override void op( double[] d0s, double err, double weight, int which) { d0s[which] += weight*Math.log(Math.max(_eps,1-err)); }
      @Override void postOp( double ds[], double wcount ) { ArrayUtils.mult(ArrayUtils.div(ds, wcount), -1); }
      @Override double classificationConstScore(Vec y, int cls) { return -1*( (y.length() - y.naCnt()) - y.bins()[cls])*Math.log(_eps) / (double) (y.length() - y.naCnt()); }
    },
    ;
    abstract void op( double[] d0, double err, double weight, int which );
    abstract void postOp( double ds[], double wcount );
    abstract double classificationConstScore(Vec y, int cls);
  }
}
