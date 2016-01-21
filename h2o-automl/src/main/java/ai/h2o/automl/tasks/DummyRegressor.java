package ai.h2o.automl.tasks;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;

/**
 * Scores the following:
 *    i. mean
 *   ii. median
 *
 *  The getDummies methods return the following strucutre:
 *
 *         double[losses][dummy values per loss]:
 *
 *         Example: losses = { MSE };
 *                  getDummies -> [ [meanMSE, medianMSE]
 *                                ]
 *
 */
public class DummyRegressor extends DummyScore {
  public static int MAGIC=2; // count of values computed

  private final double _mean;
  private final double _median;
  private DummyRegressor(Vec y) { this(y, null); }
  private DummyRegressor(Vec y, String[] losses) { super(losses); _mean=y.mean(); _median=y.pctiles()[8/*p=0.5 pctile; see Vec.PERCENTILES*/]; }

  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y) { return getDummies(y, null, null); }
  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y, Vec weight) { return getDummies(y, weight, null); }
  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y, Vec weight, String[] losses) {
    DummyRegressor dc = new DummyRegressor(y, losses);
    boolean delWeight;
    weight = (delWeight=weight==null) ? y.makeCon(1) : weight;
    dc.doAll(y,weight);
    if( delWeight ) weight.remove();
    return dc._dummies;
  }

  @Override public void setupLocal() { _dummies = new double[_losses.length][2]; }
  @Override public void map(Chunk[] cs) {
    Chunk actual = cs[0];
    int len = cs[0]._len;
    Chunk weight = cs[1];
    double[][] dummies = new double[_losses.length][2];  // only do mean, median
    for(int row=0; row<len; ++row ) {
      if( actual.isNA(row) ) continue; // skip NA
      double act = actual.atd(row);
      double w = weight.atd(row);
      _wcount += w;
      double meanErr = act - _mean;
      double medianErr  = act - _median;
      int l=0;
      System.out.println(meanErr);
      for (FCN loss : _losses) {
        loss.op(dummies[l], meanErr, w, 0);
        loss.op(dummies[l++], medianErr, w, 1);
      }
    }
    for(int l=0; l< _losses.length; ++l) {
      AtomicUtils.DoubleArray.add(_dummies[l],0,dummies[l][0]);
      AtomicUtils.DoubleArray.add(_dummies[l],1,dummies[l][1]);
    }
  }
  @Override public void reduce(DummyScore dct) {
    assert dct instanceof DummyRegressor;
    _wcount+=dct._wcount;
    if( ((DummyRegressor)dct)._dummies != _dummies )
      ArrayUtils.add(_dummies, ((DummyRegressor) dct)._dummies);
  }

  @Override public void postGlobal() {
    for(int l=0;l<_losses.length;++l)
      _losses[l].postOp(_dummies[l], _wcount);
  }
}
