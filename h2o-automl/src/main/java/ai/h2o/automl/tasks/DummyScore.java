package ai.h2o.automl.tasks;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.MRUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

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
      @Override void postOp( double ds[], double wcount ) { ArrayUtils.mult(ArrayUtils.div(ds,wcount),-1); }
      @Override double classificationConstScore(Vec y, int cls) { return -1*( (y.length() - y.naCnt()) - y.bins()[cls])*Math.log(_eps) / (double) (y.length() - y.naCnt()); }
    },
    ;
    abstract void op( double[] d0, double err, double weight, int which );
    abstract void postOp( double ds[], double wcount );
    abstract double classificationConstScore(Vec y, int cls);
  }
}

/**
 * Scores the following:
 *    i. stratified
 *   ii. most frequent
 *  iii. prior
 *   iv. random
 *    v. const
 *
 *  The getDummies methods return the following strucutre:
 *
 *         double[losses][dummy values per loss]:
 *
 *         Example: losses = { MSE, Logloss }; 3 classes = {c1, c2, c3}
 *                  getDummies -> [ [stratMSE, randomMSE, mostFreqMSE, c1MSE, c2MSE, c3MSE],
 *                                  [stratLogloss, randomLogloss, mostFreqLogloss, c1Logloss, c2Logloss, c3Logloss]
 *                                ]
 *
 */
class DummyClassifier extends DummyScore {
  // in
  private final double[] _cumDist;
  private final int _nclass;
  private final long _seed;

  // used for mrtask
  private double _dummies0[/*losses*/][/* (stratified, random) */]; // only these two need mrtask

  private DummyClassifier(Vec y) { this(y, null); }
  private DummyClassifier(Vec y, String[] losses) {
    super(losses);
    double[] classDist = new MRUtils.ClassDist(y).doAll(y).dist();
    _seed = getRNG(new Random().nextLong()).nextLong();
    _cumDist = cumDist(classDist);
    _nclass = classDist.length;
  }

  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y) { return getDummies(y, null, null); }
  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y, Vec weight) { return getDummies(y, weight, null); }
  public static double[/*losses*/][/*dummy values per loss*/] getDummies(Vec y, Vec weight, String[] losses) {
    DummyClassifier dc = new DummyClassifier(y, losses);
    boolean delWeight;
    weight = (delWeight=weight==null) ? y.makeCon(1) : weight;
    dc.doAll(y,weight);
    if( delWeight ) weight.remove();
    int maxClass = ArrayUtils.maxIndex(y.bins());
    for(int l=0;l<losses.length;++l) {
      dc._dummies[l] = new double[1+1+1+dc._nclass];  // strat, random, most freq, const[]
      dc._dummies[l][0] = dc._dummies0[l][0];
      dc._dummies[l][1] = dc._dummies0[l][1];
      dc._dummies[l][2] = dc._losses[l].classificationConstScore(y,maxClass);
      for(int c=0;c<dc._nclass;++c)
        dc._dummies[l][3+c] = dc._losses[l].classificationConstScore(y,c);
    }
    return dc._dummies;
  }

  // Conveniently, i. and iv. are the only two that require MRTask
  // for computation.
  @Override public void setupLocal() { _dummies0=new double[_losses.length][2]; }

  // predicted CLASSES are generated for the various scenarios
  // in terms of computing a loss, we make the assumption that
  // the probability for the generated class value is 1 (such
  // that all other classes have probability 0). This simplifies
  // the inner loop somewhat as the perRow error is either 0 or 1.
  @Override public void map(Chunk[] cs) {
    Chunk actual = cs[0];
    long start = cs[0].start();
    int len = cs[0]._len;
    Chunk weight = cs[1];
    double[][] dummies = new double[_losses.length][2];  // do strat and rand only
    for(int row=0; row<len; ++row ) {
      if( actual.isNA(row) ) continue; // skip NA
      long rowseed = row + start + _seed;
      int act = (int)actual.at8(row);
      double w = weight.atd(row);
      _wcount+=w;
      double stratErr = nextStratified(rowseed,_cumDist) == act ? 0 : 1;
      double randErr  = nextRandom(rowseed,_nclass) == act ? 0 : 1;
      int l=0;
      for (FCN loss : _losses) {
        loss.op(dummies[l], stratErr, w, 0);
        loss.op(dummies[l++], randErr, w, 1);
      }
    }
    for(int l=0; l< _losses.length; ++l) {
      AtomicUtils.DoubleArray.add(_dummies0[l],0,dummies[l][0]);
      AtomicUtils.DoubleArray.add(_dummies0[l],1,dummies[l][1]);
    }
  }

  @Override public void reduce(DummyScore dct) {
    assert dct instanceof DummyClassifier;
    _wcount+=dct._wcount;
    if( ((DummyClassifier)dct)._dummies0 != _dummies0 )
      ArrayUtils.add(_dummies0, ((DummyClassifier)dct)._dummies0);
  }

  @Override public void postGlobal() {
    for(int l=0;l<_losses.length;++l)
      _losses[l].postOp(_dummies0[l], _wcount);
  }

  private static int nextRandom(long rowseed, int nclass) {
    return Math.abs(getRNG(rowseed).nextInt()) % nclass;
  }

  private static int nextStratified(long rowseed, double[] cumDist) {
    double r = getRNG(rowseed).nextDouble();
    int x=0;
    for(;x<cumDist.length-1;++x)
      if( r < cumDist[x+1] ) break;
    return x;
  }

  private static double[] cumDist(double[] dist) {
    double cum=0;
    double[] cumDist=new double[dist.length];
    for(int i=1;i<dist.length;++i)
      cumDist[i]=cum+=dist[i-1];
    return cumDist;
  }
}


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
class DummyRegressor extends DummyScore {
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
      ArrayUtils.add(_dummies, ((DummyRegressor)dct)._dummies);
  }

  @Override public void postGlobal() {
    for(int l=0;l<_losses.length;++l)
      _losses[l].postOp(_dummies[l], _wcount);
  }
}
