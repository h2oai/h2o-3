package hex.tree;

import com.google.common.util.concurrent.AtomicDouble;
import sun.misc.Unsafe;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.util.*;

import java.util.Arrays;
import java.util.Random;

/** A Histogram, computed in parallel over a Vec.
 *
 *  <p>A {@code DHistogram} bins every value added to it, and computes a the
 *  vec min and max (for use in the next split), and response mean and variance
 *  for each bin.  {@code DHistogram}s are initialized with a min, max and
 *  number-of- elements to be added (all of which are generally available from
 *  a Vec).  Bins run from min to max in uniform sizes.  If the {@code
 *  DHistogram} can determine that fewer bins are needed (e.g. boolean columns
 *  run from 0 to 1, but only ever take on 2 values, so only 2 bins are
 *  needed), then fewer bins are used.
 *
 *  <p>{@code DHistogram} are shared per-node, and atomically updated.  There's
 *  an {@code add} call to help cross-node reductions.  The data is stored in
 *  primitive arrays, so it can be sent over the wire.
 *
 *  <p>If we are successively splitting rows (e.g. in a decision tree), then a
 *  fresh {@code DHistogram} for each split will dynamically re-bin the data.
 *  Each successive split will logarithmically divide the data.  At the first
 *  split, outliers will end up in their own bins - but perhaps some central
 *  bins may be very full.  At the next split(s) - if they happen at all -
 *  the full bins will get split, and again until (with a log number of splits)
 *  each bin holds roughly the same amount of data.  This 'UniformAdaptive' binning
 *  resolves a lot of problems with picking the proper bin count or limits -
 *  generally a few more tree levels will equal any fancy but fixed-size binning strategy.
 *
 *  <p>Support for histogram split points based on quantiles (or random points) is
 *  available as well, via {@code _histoType}.
 *
*/
public final class DHistogram extends Iced {
  public final transient String _name; // Column name (for debugging)
  public final double _minSplitImprovement;
  public final byte  _isInt;    // 0: float col, 1: int col, 2: categorical & int col
  public char  _nbin;     // Bin count (excluding NA bucket)
  public double _step;     // Linear interpolation step per bin
  public final double _min, _maxEx; // Conservative Min/Max over whole collection.  _maxEx is Exclusive.

  protected double [] _vals;
  public double w(int i){  return _vals[3*i+0];}
  public double wY(int i){ return _vals[3*i+1];}
  public double wYY(int i){return _vals[3*i+2];}


  public void addNasAtomic(double y, double wy, double wyy) {
    AtomicUtils.DoubleArray.add(_vals,3*_nbin+0,y);
    AtomicUtils.DoubleArray.add(_vals,3*_nbin+1,wy);
    AtomicUtils.DoubleArray.add(_vals,3*_nbin+2,wyy);
  }
  public void addNasPlain(double... ds) {
    _vals[3*_nbin+0] += ds[0];
    _vals[3*_nbin+1] += ds[1];
    _vals[3*_nbin+2] += ds[2];
  }

  public double wNA()   { return _vals[3*_nbin+0]; }
  public double wYNA()  { return _vals[3*_nbin+1]; }
  public double wYYNA() { return _vals[3*_nbin+2]; }



  // Atomically updated double min/max
  protected    double  _min2, _maxIn; // Min/Max, shared, atomically updated.  _maxIn is Inclusive.
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  static private final long _min2Offset;
  static private final long _max2Offset;
  static {
    try {
      _min2Offset = _unsafe.objectFieldOffset(DHistogram.class.getDeclaredField("_min2"));
      _max2Offset = _unsafe.objectFieldOffset(DHistogram.class.getDeclaredField("_maxIn"));
    } catch( Exception e ) {
      throw H2O.fail();
    }
  }

  public SharedTreeModel.SharedTreeParameters.HistogramType _histoType; //whether ot use random split points
  public transient double _splitPts[]; // split points between _min and _maxEx (either random or based on quantiles)
  public final long _seed;
  public transient boolean _hasQuantiles;
  public Key _globalQuantilesKey; //key under which original top-level quantiles are stored;



  /**
   * Split direction for missing values.
   *
   * Warning: If you change this enum, make sure to synchronize them with `hex.genmodel.algos.tree.NaSplitDir` in
   * package `h2o-genmodel`.
   */
  public enum NASplitDir {
    //never saw NAs in training
    None(0),     //initial state - should not be present in a trained model

    // saw NAs in training
    NAvsREST(1), //split off non-NA (left) vs NA (right)
    NALeft(2),   //NA goes left
    NARight(3),  //NA goes right

    // never NAs in training, but have a way to deal with them in scoring
    Left(4),     //test time NA should go left
    Right(5);    //test time NA should go right

    private int value;
    NASplitDir(int v) { this.value = v; }
    public int value() { return value; }
  }

  static class HistoQuantiles extends Keyed<HistoQuantiles> {
    public HistoQuantiles(Key<HistoQuantiles> key, double[] splitPts) {
      super(key);
      this.splitPts = splitPts;
    }
    double[/*nbins*/] splitPts;
  }


  public static int[] activeColumns(DHistogram[] hist) {
    int[] cols = new int[hist.length];
    int len=0;
    for( int i=0; i<hist.length; i++ ) {
      if (hist[i]==null) continue;
      assert hist[i]._min < hist[i]._maxEx && hist[i].nbins() > 1 : "broken histo range "+ hist[i];
      cols[len++] = i;        // Gather active column
    }
//    cols = Arrays.copyOfRange(cols, len, hist.length);
    return cols;
  }

  public void setMin( double min ) {
    long imin = Double.doubleToRawLongBits(min);
    double old = _min2;
    while( min < old && !_unsafe.compareAndSwapLong(this, _min2Offset, Double.doubleToRawLongBits(old), imin ) )
      old = _min2;
  }
  // Find Inclusive _max2
  public void setMaxIn( double max ) {
    long imax = Double.doubleToRawLongBits(max);
    double old = _maxIn;
    while( max > old && !_unsafe.compareAndSwapLong(this, _max2Offset, Double.doubleToRawLongBits(old), imax ) )
      old = _maxIn;
  }

  static class StepOutOfRangeException extends RuntimeException {

    public StepOutOfRangeException(double step, int xbins, double maxEx, double min) {
      super("step=" + step + ", xbins = " + xbins + ", maxEx = " + maxEx + ", min = " + min);
    }
  }
  public DHistogram(String name, final int nbins, int nbins_cats, byte isInt, double min, double maxEx,
                    double minSplitImprovement, SharedTreeModel.SharedTreeParameters.HistogramType histogramType, long seed, Key globalQuantilesKey) {
    assert nbins > 1;
    assert nbins_cats > 1;
    assert maxEx > min : "Caller ensures "+maxEx+">"+min+", since if max==min== the column "+name+" is all constants";
    _isInt = isInt;
    _name = name;
    _min=min;
    _maxEx=maxEx;               // Set Exclusive max
    _min2 = Double.MAX_VALUE;   // Set min/max to outer bounds
    _maxIn= -Double.MAX_VALUE;
    _minSplitImprovement = minSplitImprovement;
    _histoType = histogramType;
    _seed = seed;
    while (_histoType == SharedTreeModel.SharedTreeParameters.HistogramType.RoundRobin) {
      SharedTreeModel.SharedTreeParameters.HistogramType[] h = SharedTreeModel.SharedTreeParameters.HistogramType.values();
      _histoType = h[(int)Math.abs(seed++ % h.length)];
    }
    if (_histoType== SharedTreeModel.SharedTreeParameters.HistogramType.AUTO)
      _histoType= SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive;
    assert(_histoType!= SharedTreeModel.SharedTreeParameters.HistogramType.RoundRobin);
    _globalQuantilesKey = globalQuantilesKey;
    // See if we can show there are fewer unique elements than nbins.
    // Common for e.g. boolean columns, or near leaves.
    int xbins = isInt == 2 ? nbins_cats : nbins;
    if (isInt > 0 && maxEx - min <= xbins) {
      assert ((long) min) == min : "Overflow for integer/categorical histogram: minimum value cannot be cast to long without loss: (long)" + min + " != " + min + "!";                // No overflow
      xbins = (char) ((long) maxEx - (long) min);  // Shrink bins
      _step = 1.0f;                           // Fixed stepsize
    } else {
      _step = xbins / (maxEx - min);              // Step size for linear interpolation, using mul instead of div
      if(_step <= 0 || Double.isInfinite(_step) || Double.isNaN(_step))
        throw new StepOutOfRangeException(_step, xbins, maxEx, min);
    }
    _nbin = (char) xbins;
    assert(_nbin>0);
    assert(_vals ==null);

//    Log.info("Histogram: " + this);
    // Do not allocate the big arrays here; wait for scoreCols to pick which cols will be used.
  }

  // Interpolate d to find bin#
  public int bin( double col_data ) {
    if(Double.isNaN(col_data)) return _nbin; // NA bucket
    if (Double.isInfinite(col_data)) // Put infinity to most left/right bin
      if (col_data<0) return 0;
      else return _nbin-1;
    assert _min <= col_data && col_data < _maxEx : "Coldata " + col_data + " out of range " + this;
    // When the model is exposed to new test data, we could have data that is
    // out of range of any bin - however this binning call only happens during
    // model-building.
    int idx1;

    double pos = _hasQuantiles ? col_data : ((col_data - _min) * _step);
    if (_splitPts != null) {
      idx1 = Arrays.binarySearch(_splitPts, pos);
      if (idx1 < 0) idx1 = -idx1 - 2;
    } else {
      idx1 = (int) pos;
    }
    if (idx1 == _nbin) idx1--; // Roundoff error allows idx1 to hit upper bound, so truncate
    assert 0 <= idx1 && idx1 < _nbin : idx1 + " " + _nbin;
    return idx1;
  }
  public double binAt( int b ) {
    if (_hasQuantiles) return _splitPts[b];
    return _min + (_splitPts == null ? b : _splitPts[b]) / _step;
  }

  public int nbins() { return _nbin; }
  public double bins(int b) { return w(b); }

  // Big allocation of arrays
  public void init() { init(null);}
  public void init(double [] vals) {
    assert _vals == null;
    if (_histoType==SharedTreeModel.SharedTreeParameters.HistogramType.Random) {
      // every node makes the same split points
      Random rng = RandomUtils.getRNG((Double.doubleToRawLongBits(((_step+0.324)*_min+8.3425)+89.342*_maxEx) + 0xDECAF*_nbin + 0xC0FFEE*_isInt + _seed));
      assert(_nbin>1);
      _splitPts = new double[_nbin];
      _splitPts[0] = 0;
      _splitPts[_nbin - 1] = _nbin-1;
      for (int i = 1; i < _nbin-1; ++i)
         _splitPts[i] = rng.nextFloat() * (_nbin-1);
      Arrays.sort(_splitPts);
    }
    else if (_histoType== SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal) {
      assert (_splitPts == null);
      if (_globalQuantilesKey != null) {
        HistoQuantiles hq = DKV.getGet(_globalQuantilesKey);
        if (hq != null) {
          _splitPts = ((HistoQuantiles) DKV.getGet(_globalQuantilesKey)).splitPts;
          if (_splitPts!=null) {
//            Log.info("Obtaining global splitPoints: " + Arrays.toString(_splitPts));
            _splitPts = ArrayUtils.limitToRange(_splitPts, _min, _maxEx);
            if (_splitPts.length > 1 && _splitPts.length < _nbin)
              _splitPts = ArrayUtils.padUniformly(_splitPts, _nbin);
            if (_splitPts.length <= 1) {
              _splitPts = null; //abort, fall back to uniform binning
              _histoType = SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive;
            }
            else {
              _hasQuantiles=true;
              _nbin = (char)_splitPts.length;
//              Log.info("Refined splitPoints: " + Arrays.toString(_splitPts));
            }
          }
        }
      }
    }
    else assert(_histoType== SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive);
    //otherwise AUTO/UniformAdaptive
    assert(_nbin>0);
    _vals = vals == null?MemoryManager.malloc8d(3*_nbin+3):vals;
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute bin min/max.
  // Compute response mean & variance.
  void incr( double col_data, double y, double w ) {
    if (Double.isNaN(col_data)) {
      addNasAtomic(w,w*y,w*y*y);
      return;
    }
    assert Double.isInfinite(col_data) || (_min <= col_data && col_data < _maxEx) : "col_data "+col_data+" out of range "+this;
    int b = bin(col_data);      // Compute bin# via linear interpolation
    water.util.AtomicUtils.DoubleArray.add(_vals,3*b,w); // Bump count in bin
    // Track actual lower/upper bound per-bin
    if (!Double.isInfinite(col_data)) {
      setMin(col_data);
      setMaxIn(col_data);
    }
    if( y != 0 && w != 0) incr0(b,y,w);
  }

  // Merge two equal histograms together.  Done in a F/J reduce, so no
  // synchronization needed.
  public void add( DHistogram dsh ) {
    assert (_vals == null || dsh._vals == null) || (_isInt == dsh._isInt && _nbin == dsh._nbin && _step == dsh._step &&
      _min == dsh._min && _maxEx == dsh._maxEx);
    if( dsh._vals == null ) return;
    if(_vals == null)
      init(dsh._vals);
    else
      ArrayUtils.add(_vals,dsh._vals);
    if (_min2 > dsh._min2) _min2 = dsh._min2;
    if (_maxIn < dsh._maxIn) _maxIn = dsh._maxIn;
  }

  // Inclusive min & max
  public double find_min  () { return _min2 ; }
  public double find_maxIn() { return _maxIn; }
  // Exclusive max
  public double find_maxEx() { return find_maxEx(_maxIn,_isInt); }
  public static double find_maxEx(double maxIn, int isInt ) {
    double ulp = Math.ulp(maxIn);
    if( isInt > 0 && 1 > ulp ) ulp = 1;
    double res = maxIn+ulp;
    return Double.isInfinite(res) ? maxIn : res;
  }

  // The initial histogram bins are setup from the Vec rollups.
  public static DHistogram[] initialHist(Frame fr, int ncols, int nbins, DHistogram hs[], long seed, SharedTreeModel.SharedTreeParameters parms, Key[] globalQuantilesKey) {
    Vec vecs[] = fr.vecs();
    for( int c=0; c<ncols; c++ ) {
      Vec v = vecs[c];
      final double minIn = Math.max(v.min(),-Double.MAX_VALUE); // inclusive vector min
      final double maxIn = Math.min(v.max(), Double.MAX_VALUE); // inclusive vector max
      final double maxEx = find_maxEx(maxIn,v.isInt()?1:0);     // smallest exclusive max
      final long vlen = v.length();
      try {
        hs[c] = v.naCnt() == vlen || v.min() == v.max() ?
            null : make(fr._names[c], nbins, (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0)), minIn, maxEx, seed, parms, globalQuantilesKey[c]);
      } catch(StepOutOfRangeException e) {
        hs[c] = null;
        Log.warn("Column " + fr._names[c]  + " with min = " + v.min() + ", max = " + v.max() + " has step out of range (" + e.getMessage() + ") and is ignored.");
      }
      assert (hs[c] == null || vlen > 0);
    }
    return hs;
  }



  public static DHistogram make(String name, final int nbins, byte isInt, double min, double maxEx, long seed, SharedTreeModel.SharedTreeParameters parms, Key globalQuantilesKey) {
    return new DHistogram(name,nbins, parms._nbins_cats, isInt, min, maxEx, parms._min_split_improvement, parms._histogram_type, seed, globalQuantilesKey);
  }

  // Pretty-print a histogram
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(_name).append(":").append(_min).append("-").append(_maxEx).append(" step=" + (1 / _step) + " nbins=" + nbins() + " isInt=" + _isInt);
    if( _vals != null ) {
      for(int b = 0; b< _nbin; b++ ) {
        sb.append(String.format("\ncnt=%f, [%f - %f], mean/var=", w(b),_min+b/_step,_min+(b+1)/_step));
        sb.append(String.format("%6.2f/%6.2f,", mean(b), var(b)));
      }
      sb.append('\n');
    }
    return sb.toString();
  }
  double mean(int b) {
    double n = w(b);
    return n>0 ? wY(b)/n : 0;
  }

  /**
   * compute the sample variance within a given bin
   * @param b bin id
   * @return sample variance (>= 0)
   */
  public double var (int b) {
    double n = w(b);
    if( n<=1 ) return 0;
    return Math.max(0, (wYY(b) - wY(b)* wY(b)/n)/(n-1)); //not strictly consistent with what is done elsewhere (use n instead of n-1 to get there)
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute response mean & variance.
  // Done racily instead F/J map calls, so atomic
  public void incr0( int b, double y, double w ) {
    AtomicUtils.DoubleArray.add(_vals,3*b+1,(float)(w*y)); //See 'HistogramTest' JUnit for float-casting rationalization
    AtomicUtils.DoubleArray.add(_vals,3*b+2,(float)(w*y*y));
  }
  // Same, except square done by caller
  public void incr1( int b, double y, double yy) {
    AtomicUtils.DoubleArray.add(_vals,3*b+1,(float)y); //See 'HistogramTest' JUnit for float-casting rationalization
    AtomicUtils.DoubleArray.add(_vals,3*b+2,(float)yy);
  }

  /**
   * Update counts in appropriate bins. Not thread safe, assumed to have private copy.
   * @param ws observation weights
   * @param cs column data
   * @param ys response
   * @param rows rows sorted by leaf assignemnt
   * @param hi  upper bound on index into rows array to be processed by this call (exclusive)
   * @param lo  lower bound on index into rows array to be processed by this call (inclusive)
   */
  public void updateHisto(double[] ws, double[] cs, double[] ys, int [] rows, int hi, int lo){
    // Gather all the data for this set of rows, for 1 column and 1 split/NID
    // Gather min/max, wY and sum-squares.
    for(int r = lo; r< hi; ++r) {
      int k = rows[r];
      double weight = ws[k];
      if (weight == 0) continue;
      double col_data = cs[k];
      if (col_data < _min2) _min2 = col_data;
      if (col_data > _maxIn) _maxIn = col_data;
      double y = ys[k];
      assert (!Double.isNaN(y));
      double wy = weight * y;
      double wyy = wy * y;
      int b = bin(col_data);
      _vals[3*b + 0] += weight;
      _vals[3*b + 1] += wy;
      _vals[3*b + 2] += wyy;
    }
  }

  /**
   * Cast bin values *except for sums of weights and Na-bucket counters to floats to drop least significant bits.
   * Improves reproducibility (drop bits most affected by floating point error).
   */
  public void reducePrecision(){
    if(_vals == null) return;
    for(int i = 0; i < _vals.length -3 /* do not reduce precision of NAs */; i+=3) {
      _vals[i+1] = (float)_vals[i+1];
      _vals[i+2] = (float)_vals[i+2];
    }
  }

  public void updateSharedHistosAndReset(ScoreBuildHistogram.LocalHisto lh, double[] ws, double[] cs, double[] ys, int [] rows, int hi, int lo) {
    double minmax[] = new double[]{_min2,_maxIn};
    // Gather all the data for this set of rows, for 1 column and 1 split/NID
    // Gather min/max, wY and sum-squares.
    for(int r = lo; r< hi; ++r) {
      int k = rows[r];
      double weight = ws[k];
      if (weight == 0) continue;
      double col_data = cs[k];
      if( col_data < minmax[0] ) minmax[0] = col_data;
      if( col_data > minmax[1] ) minmax[1] = col_data;
      double y = ys[k];
      assert(!Double.isNaN(y));
      double wy = weight * y;
      double wyy = wy * y;
      if (Double.isNaN(col_data)) {
        //separate bucket for NA - atomically added to the shared histo
        addNasAtomic(weight,wy,wyy);
      } else {
        // increment local pre-thread histograms
        int b = bin(col_data);
        lh.wAdd(b,weight);
        lh.wYAdd(b,wy);
        lh.wYYAdd(b,wyy);
      }
    }
    // Atomically update histograms
    setMin(minmax[0]);       // Track actual lower/upper bound per-bin
    setMaxIn(minmax[1]);
    final int len = _nbin;
    for( int b=0; b<len; b++ ) {
      if (lh.w(b) != 0) {
        AtomicUtils.DoubleArray.add(_vals, 3*b+0, lh.w(b));
        lh.wClear(b);
      }
      if (lh.wY(b) != 0) {
        AtomicUtils.DoubleArray.add(_vals, 3*b+1, (float) lh.wY(b));
        lh.wYClear(b);
      }
      if (lh.wYY(b) != 0) {
        AtomicUtils.DoubleArray.add(_vals, 3*b+2,(float)lh.wYY(b));
        lh.wYYClear(b);
      }
    }
  }

}
