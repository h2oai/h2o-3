package ai.h2o.automl;


import ai.h2o.automl.guessers.ColNameScanner;
import ai.h2o.automl.guessers.ProblemTypeGuesser;
import sun.misc.Unsafe;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.util.ArrayUtils;

import java.util.Arrays;

/** Column Meta Data
 *
 * Holds usual rollup stats and additional interesting bits of information.
 */
public class ColMeta extends Iced {
  public final Vec _v;  // the column
  public final byte _nameType;  // guessed at by ColNameScanner
  public final String _name;
  public final int _idx;

  public boolean _ignored;  // should this column be ignored outright
  public boolean _response; // is this a response column?

  // FIRST PASS

  // (discussion) need to have guardrails around data and possibly some warning or error
  // about data that doesn't fit the distribution that was trained. Will want to compare
  // histos from training and testing data to see if they "match". WTM
  public DynamicHisto _histo;
  public long _timeToMRTask;
  public double _percentNA;  // fraction of NAs in the column
  public SpecialNA _specialNAs; // found special NAs like 9999 or -1 or 0
  private boolean _isClassification;


  // SECOND PASS
  // https://0xdata.atlassian.net/browse/STEAM-41 --column metadata to gather
  public long _numUniq;
  public double _numUniqPerChunk;

  public boolean  _chunksMonotonicallyIncreasing;  //
  public double[] _chkBndries;                     // boundary values for each chunk [c1.min, c1.max, c2.min, c2.max, ...]; only for numeric vecs

  public double _kurtosis;
  public double _skew;
  public double _median;

  // is this an ID?
  //   - all unique
  //   - increasing ints
  public boolean _isID;

  // is it a date column?
  //   - has date info (possibly a month column since values are all 1-12, or 0-11)
  //   - possibly next to other date columns?
  public boolean _isDate;

  public ColMeta(Vec v, String colname, int idx, boolean response) {
    _v = v;
    _name = colname;
    _nameType = ColNameScanner.scan(_name);
    _idx = idx;
    _response = response;
    _specialNAs = new SpecialNA(
            _v.isNumeric()
                    ? (_v.isInt() ? SpecialNA.INT : SpecialNA.DBL)
                    : SpecialNA.STR
    );
    _percentNA = (double)v.naCnt() / (double)v.length();
    _ignored = v.isConst() || v.isString() || v.isBad() || v.isUUID(); // auto ignore from the outset
    if( _response )
      _isClassification = ProblemTypeGuesser.guess(v);
  }

  public boolean isClassification() {
    if( !_response ) throw new UnsupportedOperationException("not a response column");
    return _isClassification;
  }

  // stupid wrapper class for possibly special types of NAs; things like 999999 or -1 or 0
  // https://0xdata.atlassian.net/browse/STEAM-76
  static class SpecialNA extends Iced {
    int[] _ints;
    double[] _dbls;
    String[] _strs;

    byte _type;
    int _idx;
    private static final byte INT=0;
    private static final byte DBL=1;
    private static final byte STR=2;

    SpecialNA(byte type) {
      _type=type;
      switch(type) {
        case INT: _ints=new    int[4]; break;
        case DBL: _dbls=new double[4]; break;
        case STR: _strs=new String[4]; break;
      }
      _idx=0;
    }

    public void add(int val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _ints = Arrays.copyOf(_ints, _idx >> 1);
        _ints[_idx++]=val;
      }
    }
    public void add(double val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _dbls = Arrays.copyOf(_dbls, _idx >> 1);
        _dbls[_idx++]=val;
      }
    }
    public void add(String val) {
      synchronized (this) {
        if( _idx==_ints.length )
          _strs = Arrays.copyOf(_strs, _idx >> 1);
        _strs[_idx++]=val;
      }
    }
  }

  // folds together ideas from ASTHist and DHistogram
  public static class DynamicHisto extends Iced {

  }

  private static class HistTask extends MRTask<HistTask> {
    final private double _h;      // bin width
    final private double _x0;     // far left bin edge
    final private double[] _min;  // min for each bin, updated atomically
    final private double[] _max;  // max for each bin, updated atomically
    // unsafe crap for mins/maxs of bins
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    // double[] offset and scale
    private static final int _dB = U.arrayBaseOffset(double[].class);
    private static final int _dS = U.arrayIndexScale(double[].class);
    private static long doubleRawIdx(int i) { return _dB + _dS * i; }
    // long[] offset and scale
    private static final int _8B = U.arrayBaseOffset(long[].class);
    private static final int _8S = U.arrayIndexScale(long[].class);
    private static long longRawIdx(int i)   { return _8B + _8S * i; }

    // out
    private final double[] _breaks;
    private final long  [] _counts;
    private final double[] _mids;

    HistTask(double[] cuts, double h, double x0) {
      _breaks=cuts;
      _min=new double[_breaks.length-1];
      _max=new double[_breaks.length-1];
      _counts=new long[_breaks.length-1];
      _mids=new double[_breaks.length-1];
      _h=h;
      _x0=x0;
    }
    @Override public void map(Chunk c) {
      // if _h==-1, then don't have fixed bin widths... must loop over bins to obtain the correct bin #
      for( int i = 0; i < c._len; ++i ) {
        int x=1;
        if( c.isNA(i) ) continue;
        double r = c.atd(i);
        if( _h==-1 ) {
          for(; x < _counts.length; x++)
            if( r <= _breaks[x] ) break;
          x--; // back into the bin where count should go
        } else
          x = Math.min( _counts.length-1, (int)Math.floor( (r-_x0) / _h ) );     // Pick the bin   floor( (x - x0) / h ) or ceil( (x-x0)/h - 1 ), choose the first since fewer ops
        bumpCount(x);
        setMinMax(Double.doubleToRawLongBits(r),x);
      }
    }
    @Override public void reduce(HistTask t) {
      if(_counts!=t._counts) ArrayUtils.add(_counts, t._counts);
      for(int i=0;i<_mids.length;++i) {
        _min[i] = t._min[i] < _min[i] ? t._min[i] : _min[i];
        _max[i] = t._max[i] > _max[i] ? t._max[i] : _max[i];
      }
    }
    @Override public void postGlobal() { for(int i=0;i<_mids.length;++i) _mids[i] = 0.5*(_max[i] + _min[i]); }

    private void bumpCount(int x) {
      long o = _counts[x];
      while(!U.compareAndSwapLong(_counts,longRawIdx(x),o,o+1))
        o=_counts[x];
    }
    private void setMinMax(long v, int x) {
      double o = _min[x];
      double vv = Double.longBitsToDouble(v);
      while( vv < o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _min[x];
      setMax(v,x);
    }
    private void setMax(long v, int x) {
      double o = _max[x];
      double vv = Double.longBitsToDouble(v);
      while( vv > o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _max[x];
    }
  }
}