package ai.h2o.automl.tasks;

import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.util.ArrayUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

/**
 *
 */
abstract class DummyScore extends MRTask<DummyScore> {

  // TODO: allow user to specify some custom loss
  enum FCN {
    mse() {
      @Override void op( double[] d0s, double err, double weight, int which) { d0s[which] += weight*err*err; }
      @Override void atomOp( double[] d0s, double[] d1s ) { ArrayUtils.add(d0s, d1s); }
      @Override double[] postOp( double ds[], double wcount ) { return ArrayUtils.div(ds,wcount); }
    },
    logloss() {
      double _eps=1e-15;
      @Override void op( double[] d0s, double err, double weight, int which) { d0s[which] -= weight*Math.log(Math.max(_eps,1-err)); }
      @Override void atomOp( double[] d0s, double[] d1s ) { ArrayUtils.subtract(d0s,d1s); } // TODO: this needs a test, VERY BADLY
      @Override double[] postOp( double ds[], double wcount ) { return ArrayUtils.div(ds,wcount); }
    },
    ;
    abstract void op( double[] d0, double err, double weight, int which );
    abstract void atomOp( double[] d0, double[] d1 );
    abstract double[] postOp( double ds[], double wcount );
    double[] initVal(int m) { return new double[]{m}; }
  }
}

/**
 * Scores the following:
 *    i. stratified
 *   ii. most frequent
 *  iii. prior
 *   iv. random
 *    v. const
 */
class DummyClassifier extends DummyScore {
  // in
  private final double[] _cumDist;
  private final int _nclass;
  private final FCN[] _losses;
  private final long _seed;

  // out
  double _dummies[/* (stratified, mostFrequent, prior, random, const[]) == 4 + _nclass */]; // const is 1 per class
  double _wcount;

  DummyClassifier(double[] classDist, String[] losses) {
    _seed = getRNG(new Random().nextLong()).nextLong();
    _cumDist = cumDist(classDist);
    _dummies = new double[4 + (_nclass=classDist.length)];
    _losses = new FCN[losses==null?1:losses.length];
    if( losses==null ) _losses[0] = FCN.valueOf("mse");  // default to mse
    else {
      int i=0;
      for (String loss : losses) _losses[i++] = FCN.valueOf(loss);
    }
  }

  // predicted CLASSES are generated for the various scenarios
  // in terms of computing a loss, we make the assumption that
  // the probability for the generated class value is 1 (such
  // that all other classes have probability 0). This simplifies
  // the inner loop somewhat as the perRow error is either 0 or 1.
  @Override public void map(Chunk[] cs) {
    Chunk actual = cs[0];
    long start = cs[0].start();
    int len = cs[0]._len;
    Chunk weight = cs.length > 1 ? cs[1] : new C0DChunk(1,len);
    double[] dummies = new double[_dummies.length];
    for(int row=0; row<len; ++row ) {
      int act = (int)actual.at8(row);
      double w = weight.atd(row);
      double stratErr = nextStratified(row+start+_seed,_cumDist) == act ? 0 : 1;
      for (int i = 0; i < _losses.length; ++i) {
        _losses[i].op(dummies, stratErr, w, 0);
        _losses[i].op(dummies, , w, 1);
        _losses[i].op(dummies, , w, 2);
        _losses[i].op(dummies, , w, 3);
        for(int c=4;c<4+_nclass;++c)
          _losses[i].op(dummies,c-4==act?0:1,w,c);
      }
    }
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


class DummyRegressor extends DummyScore {
}
