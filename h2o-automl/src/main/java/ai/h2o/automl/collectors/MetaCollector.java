package ai.h2o.automl.collectors;


import water.util.ArrayUtils;

/**
 * Collect metadata over a single column.
 */
public class MetaCollector {
  enum COLLECT {
    skew() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]++; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    kurtosis() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]/n; }
    },
    uniqPerChk() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    timePerChunk() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n) { return ds[0]; }
    },
    mode() {
      @Override void op( double[] d0s, double d1 ) { d0s[(int)d1]++; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { ArrayUtils.add(d0s,d1s); }
      @Override double postPass( double ds[], long n ) { return ArrayUtils.maxIndex(ds); }
      @Override double[] initVal(int maxx) { return new double[maxx]; }
    },
    ;
    abstract void op( double[] d0, double d1 );
    abstract void atomic_op( double[] d0, double[] d1 );
    abstract double postPass( double ds[], long n );
    double[] initVal(int maxx) { return new double[]{0}; }
  }
  // TODO: add hiddenNAFinder https://0xdata.atlassian.net/browse/STEAM-76
}