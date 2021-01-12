package hex.tree;

import hex.Distribution;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.uplift.*;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

import static hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;

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
public final class DHistogram extends Iced<DHistogram> {
  
  private static final Logger LOG = Logger.getLogger(DHistogram.class);

  public static final int INT_NA = Integer.MIN_VALUE; // integer representation of NA

  public final transient String _name; // Column name (for debugging)
  public final double _minSplitImprovement;
  public final byte  _isInt;    // 0: float col, 1: int col, 2: categorical & int col
  public final boolean _intOpt;
  public char  _nbin;     // Bin count (excluding NA bucket)
  public double _step;     // Linear interpolation step per bin
  public final double _min, _maxEx; // Conservative Min/Max over whole collection.  _maxEx is Exclusive.
  public final int _minInt;         // Integer version of _min. Used in integer optimized histograms. 
  public final boolean _initNA;  // Does the initial histogram have any NAs? 
                                 // Needed to correctly count actual number of bins of the initial histogram. 
  public final double _pred1; // We calculate what would be the SE for a possible fallback predictions _pred1
  public final double _pred2; // and _pred2. Currently used for min-max bounds in monotonic GBMs.

  protected double [] _vals; // Values w, wY and wYY encoded per bin in a single array. 
                             // If _pred1 or _pred2 are specified they are included as well.
                             // If constraints are used and gamma denominator or nominator needs to be calculated its will be included.
  protected final int _vals_dim; // _vals.length == _vals_dim * _nbin; How many values per bin are encoded in _vals.
                                 // Current possible values are
                                 // - 3:_pred1 nor _pred2 provided and gamma denominator is not needed 
                                 // - 5: if either _pred1 or _pred2 is provided (or both)
                                 //      - 5 if gamma denominator and nominator are not needed
                                 //      - 6 if gamma denominator is needed
                                 //      - 7 if gamma nominator is needed (tweedie constraints)
                                 // also see functions hasPreds() and hasDenominator()
                                 
  protected final boolean _useUplift; 
  protected double [] _valsUplift; // if not null always dimension 4: 
                                  // 0 treatment group nominator 
                                  // 1 treatment group denominator
                                  // 2 control group nominator
                                  // 3 control group denominator
  protected final int _valsDimUplift = 4;
  protected final Divergence _upliftMetric;
                                  
  private final Distribution _dist;

  protected double  _min2, _maxIn; // Min/Max, _maxIn is Inclusive.
  public HistogramType _histoType; //whether ot use random split points
  transient double _splitPts[]; // split points between _min and _maxEx (either random or based on quantiles)
  transient int _zeroSplitPntPos;
  public final boolean _checkFloatSplits;
  transient float[] _splitPtsFloat;
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

  static class StepOutOfRangeException extends RuntimeException {

    public StepOutOfRangeException(String name, double step, int xbins, double maxEx, double min) {
      super("column=" + name + " leads to invalid histogram(check numeric range) -> [max=" + maxEx + ", min = " + min + "], step= " + step + ", xbin= " + xbins);
    }
  }

  DHistogram(String name, final int nbins, int nbins_cats, byte isInt, double min, double maxEx, boolean intOpt, boolean initNA,
             double minSplitImprovement, SharedTreeModel.SharedTreeParameters.HistogramType histogramType, long seed, Key globalQuantilesKey,
             Constraints cs, boolean checkFloatSplits) {
    assert nbins >= 1;
    assert nbins_cats >= 1;
    assert maxEx > min : "Caller ensures "+maxEx+">"+min+", since if max==min== the column "+name+" is all constants";
    if (cs != null) {
      _pred1 = cs._min;
      _pred2 = cs._max;
      if (!cs.needsGammaDenom() && !cs.needsGammaNom()) {
        _vals_dim = Double.isNaN(_pred1) && Double.isNaN(_pred2) ? 3 : 5;
      } else if (!cs.needsGammaNom()) {
        _vals_dim = 6;
      } else {
        _vals_dim = 7;
      }
      _dist = cs._dist;
    } else {
      _pred1 = Double.NaN;
      _pred2 = Double.NaN;
      _vals_dim = 3;
      _dist = null;
    }
    _isInt = isInt;
    _name = name;
    _min = min;
    _minInt = (int) min;
    _maxEx = maxEx;             // Set Exclusive max
    _min2 = Double.MAX_VALUE;   // Set min/max to outer bounds
    _maxIn= -Double.MAX_VALUE;
    _intOpt = intOpt;
    _initNA = initNA;
    _minSplitImprovement = minSplitImprovement;
    _histoType = histogramType;
    _useUplift = false;
    _upliftMetric = null;
    _seed = seed;
    while (_histoType == HistogramType.RoundRobin) {
      HistogramType[] h = HistogramType.values();
      _histoType = h[(int)Math.abs(seed++ % h.length)];
    }
    if (_histoType== HistogramType.AUTO)
      _histoType= HistogramType.UniformAdaptive;
    assert(_histoType!= HistogramType.RoundRobin);
    _globalQuantilesKey = globalQuantilesKey;
    // See if we can show there are fewer unique elements than nbins.
    // Common for e.g. boolean columns, or near leaves.
    int xbins = isInt == 2 ? nbins_cats : nbins;
    if (isInt > 0 && maxEx - min <= xbins) {
      assert ((long) min) == min : "Overflow for integer/categorical histogram: minimum value cannot be cast to long without loss: (long)" + min + " != " + min + "!";                // No overflow
      xbins = (char) ((long) maxEx - (long) min);  // Shrink bins
      _step = 1.0f;                           // Fixed step size
    } else {
      _step = xbins / (maxEx - min);              // Step size for linear interpolation, using mul instead of div
      if(_step <= 0 || Double.isInfinite(_step) || Double.isNaN(_step))
        throw new StepOutOfRangeException(name,_step, xbins, maxEx, min);
    }
    _nbin = (char) xbins;
    assert(_nbin>0);
    assert(_vals == null);
    _checkFloatSplits = checkFloatSplits;

    if (LOG.isTraceEnabled()) LOG.trace("Histogram: " + this);
    // Do not allocate the big arrays here; wait for scoreCols to pick which cols will be used.
  }

  DHistogram(String name, final int nbins, int nbins_cats, byte isInt, double min, double maxEx, boolean intOpt, boolean initNA,
             double minSplitImprovement, SharedTreeModel.SharedTreeParameters.HistogramType histogramType, long seed, Key globalQuantilesKey,
             Constraints cs, boolean checkFloatSplits, boolean useUplift, UpliftDRFModel.UpliftDRFParameters.UpliftMetricType upliftMetricType) {
    assert nbins >= 1;
    assert nbins_cats >= 1;
    assert maxEx > min : "Caller ensures "+maxEx+">"+min+", since if max==min== the column "+name+" is all constants";
    if (cs != null) {
      _pred1 = cs._min;
      _pred2 = cs._max;
      if (!cs.needsGammaDenom() && !cs.needsGammaNom()) {
        _vals_dim = Double.isNaN(_pred1) && Double.isNaN(_pred2) ? 3 : 5;
      } else if (!cs.needsGammaNom()) {
        _vals_dim = 6;
      } else {
        _vals_dim = 7;
      }
      _dist = cs._dist;
    } else {
      _pred1 = Double.NaN;
      _pred2 = Double.NaN;
      _vals_dim = 3;
      _dist = null;
    }
    _isInt = isInt;
    _name = name;
    _min = min;
    _maxEx = maxEx;             // Set Exclusive max
    _min2 = Double.MAX_VALUE;   // Set min/max to outer bounds
    _maxIn= -Double.MAX_VALUE;
    _initNA = initNA;
    _intOpt = intOpt;
    _minInt = (int) min;
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
        throw new StepOutOfRangeException(name,_step, xbins, maxEx, min);
    }
    _nbin = (char) xbins;
    _useUplift = useUplift;
    if (useUplift) {
      switch (upliftMetricType) {
        case ChiSquared:
          _upliftMetric = new ChiSquaredDivergence();
          break;
        case Euclidean:
          _upliftMetric = new EuclideanDistance();
          break;
        default:
          _upliftMetric = new KLDivergence();
      }
    } else {
      _upliftMetric = null;
    }
    assert(_nbin>0);
    assert(_vals == null);
    _checkFloatSplits = checkFloatSplits;
    if (LOG.isTraceEnabled()) LOG.trace("Histogram: " + this);
    // Do not allocate the big arrays here; wait for scoreCols to pick which cols will be used.
  }

  /**
   * Interpolate d to find bin#
   * @param col_data column data
   * @return index of the bin
   */
  public int bin(final double col_data) {
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
      idx1 = pos == 0.0 ? _zeroSplitPntPos : Arrays.binarySearch(_splitPts, pos);
      if (idx1 < 0) idx1 = -idx1 - 2;
    } else {
      idx1 = (int) pos;
    }
    if (_splitPtsFloat != null) {
      if (idx1 + 1 < _splitPtsFloat.length) {
        float splitAt = _splitPtsFloat[idx1 + 1];
        if (col_data >= splitAt) {
          idx1++;
        }
        if (idx1 > 0) {
          if (!(col_data >= _splitPtsFloat[idx1])) {
            idx1--;
          }
        }
      }
    }
    if (idx1 == _nbin)
      idx1--; // Round-off error allows idx1 to hit upper bound, so truncate
    assert 0 <= idx1 && idx1 < _nbin : idx1 + " " + _nbin;
    return idx1;
  }

  public double binAt( int b ) {
    if (_hasQuantiles) return _splitPts[b];
    return _min + (_splitPts == null ? b : _splitPts[b]) / _step;
  }

  /**
   * Get number of bins excluding the NA bin
   * @return number of bins excluding the NA bin
   */
  public int nbins() { return _nbin; }

  /**
   * Get actual number of bins (possibly including NA bin)
   * @return actual number of bins (possibly including NA bin)
   */
  public int actNBins() {
    return nbins() + (hasNABin() ? 1 : 0);
  }
  
  public double bins(int b) { return w(b); }


  public boolean hasNABin() {
    if (_vals == null)
      return _initNA; // we are in the initial histogram (and didn't see the data yet)
    else
      return wNA() > 0;
  }
  
  /**
   * Initialize DHistogram without vals
   * Big allocation of arrays
   */
  public void init() { init(null, null);}

  /**
   * Initialize DHistogram without vals
   * Big allocation of arrays
   */
  public void init(double[] vals) { init(vals, null);}

  /**
   * Initialize DHistogram with vals
   * Big allocation of arrays
   * @param vals initialization values
   */
  public void init(final double[] vals, double[] valsUplift) {
    assert _vals == null;
    if (_histoType==HistogramType.Random) {
      // every node makes the same split points
      Random rng = RandomUtils.getRNG((Double.doubleToRawLongBits(((_step+0.324)*_min+8.3425)+89.342*_maxEx) + 0xDECAF*_nbin + 0xC0FFEE*_isInt + _seed));
      assert _nbin > 1;
      _splitPts = makeRandomSplitPoints(_nbin, rng);
    }
    else if (_histoType== HistogramType.QuantilesGlobal) {
      assert (_splitPts == null);
      if (_globalQuantilesKey != null) {
        HistoQuantiles hq = DKV.getGet(_globalQuantilesKey);
        if (hq != null) {
          _splitPts = ((HistoQuantiles) DKV.getGet(_globalQuantilesKey)).splitPts;
          if (_splitPts!=null) {
            if (LOG.isTraceEnabled()) LOG.trace("Obtaining global splitPoints: " + Arrays.toString(_splitPts));
            _splitPts = ArrayUtils.limitToRange(_splitPts, _min, _maxEx);
            if (_splitPts.length > 1 && _splitPts.length < _nbin)
              _splitPts = ArrayUtils.padUniformly(_splitPts, _nbin);
            if (_splitPts.length <= 1) {
              _splitPts = null; //abort, fall back to uniform binning
              _histoType = HistogramType.UniformAdaptive;
            }
            else {
              _hasQuantiles=true;
              _nbin = (char)_splitPts.length;
              if (LOG.isTraceEnabled()) LOG.trace("Refined splitPoints: " + Arrays.toString(_splitPts));
            }
          }
        }
      }
    }
    else assert(_histoType== HistogramType.UniformAdaptive);
    if (_splitPts != null) {
      // Inject canonical representation of zero - convert "negative zero" to 0.0d
      // This is for PUBDEV-7161 - Arrays.binarySearch used in bin() method is not able to find a negative zero,
      // we always use 0.0d instead
      // We also cache the position of zero in the split points for a faster lookup
      _zeroSplitPntPos = Arrays.binarySearch(_splitPts, 0.0d);
      if (_zeroSplitPntPos < 0) {
        int nzPos = Arrays.binarySearch(_splitPts, -0.0d);
        if (nzPos >= 0) {
          _splitPts[nzPos] = 0.0d;
          _zeroSplitPntPos = nzPos;
        }
      }
    }
    // otherwise AUTO/UniformAdaptive
    _vals = vals == null ? MemoryManager.malloc8d(_vals_dim * _nbin + _vals_dim) : vals;
    _valsUplift = valsUplift == null ? MemoryManager.malloc8d(_valsDimUplift * _nbin + _valsDimUplift) : valsUplift;
    // this always holds: _vals != null
    assert _nbin > 0;
    if (_checkFloatSplits) {
      _splitPtsFloat = new float[_nbin];
      for (int i = 0; i < _nbin; i++) {
        _splitPtsFloat[i] = (float) binAt(i);
      }
    }
    assert !_intOpt || _splitPts == null : "Integer-optimization cannot be enabled when split points are defined";
    assert !_intOpt || _histoType == HistogramType.UniformAdaptive : "Integer-optimization can only be enabled for histogram type 'UniformAdaptive'.";
  }
  

  public double w(int i){  return _vals[_vals_dim*i+0];}
  
  public double wY(int i){ return _vals[_vals_dim*i+1];}
  
  public double wYY(int i){return _vals[_vals_dim*i+2];}

  public void addWAtomic(int i, double wDelta) {  // used by AutoML
    AtomicUtils.DoubleArray.add(_vals, _vals_dim*i+0, wDelta);
  }
  
  public double wNA()   { return _vals[_vals_dim*_nbin+0]; }
  
  public double wYNA()  { return _vals[_vals_dim*_nbin+1]; }
  
  public double wYYNA() { return _vals[_vals_dim*_nbin+2]; }

  /**
   * Squared Error for NA bucket and prediction value _pred1
   * @return se
   */
  public double seP1NA() { return _vals[_vals_dim*_nbin+3]; }
  
  /**
   * Squared Error for NA bucket and prediction value _pred2
   * @return se
   */
  public double seP2NA() { return _vals[_vals_dim*_nbin+4]; }
  
  public double denNA() { return _vals[_vals_dim*_nbin+5]; }
  
  public double nomNA() { return _vals[_vals_dim*_nbin+6]; }

  public double numTreatmentNA() { return _valsUplift[_valsDimUplift*_nbin]; }
  
  public double respTreatmentNA() { return _valsUplift[_valsDimUplift*_nbin+1]; }
  
  public double numControlNA() { return _valsUplift[_valsDimUplift*_nbin+2]; }

  public double respControlNA() { return _valsUplift[_valsDimUplift*_nbin+3]; }
  

  final boolean hasPreds() {
    return _vals_dim >= 5;
  }

  final boolean hasDenominator() {
    return _vals_dim >= 6;
  }

  final boolean hasNominator() {
    return _vals_dim == 7;
  }
  
  final boolean useUplift(){
    return _useUplift;
  }
  
  public void add( DHistogram dsh ) {
    assert (_vals == null || dsh._vals == null) || (_isInt == dsh._isInt && _nbin == dsh._nbin && _step == dsh._step &&
      _min == dsh._min && _maxEx == dsh._maxEx);
    if( dsh._vals == null ) return;
    if(_vals == null) {
      init(dsh._vals, dsh._valsUplift);
    } else {
      ArrayUtils.add(_vals, dsh._vals);
      ArrayUtils.add(_valsUplift, dsh._valsUplift);
    }
    if (_min2 > dsh._min2) _min2 = dsh._min2;

    if (_maxIn < dsh._maxIn) _maxIn = dsh._maxIn;
  }

  // Inclusive min & max
  public double findMin() { return _min2 ; }
  
  public double find_maxIn() { return _maxIn; }
  
  // Exclusive max
  public double find_maxEx() { return find_maxEx(_maxIn,_isInt); }
  
  public static double find_maxEx(double maxIn, int isInt ) {
    double ulp = Math.ulp(maxIn);
    if( isInt > 0 && 1 > ulp ) ulp = 1;
    double res = maxIn+ulp;
    return Double.isInfinite(res) ? maxIn : res;
  }

  /**
   * The initial histogram bins are setup from the Vec rollups.
   * @param fr frame with column data
   * @param ncols number of columns
   * @param nbins number of bins
   * @param hs an array of histograms to be initialize
   * @param seed seed to reproduce
   * @param parms parameters of the model
   * @param globalQuantilesKey array of global quantile keys
   * @param cs monotone constraints (could be null)
   * @param checkFloatSplits          
   * @return array of DHistograms objects 
   */
  public static DHistogram[] initialHist(Frame fr, int ncols, int nbins, DHistogram hs[], long seed, SharedTreeModel.SharedTreeParameters parms, Key[] globalQuantilesKey, 
                                         Constraints cs, boolean checkFloatSplits) {

    Vec vecs[] = fr.vecs();
    for( int c=0; c<ncols; c++ ) {
      Vec v = vecs[c];
      final double minIn = v.isCategorical() ? 0 : Math.max(v.min(),-Double.MAX_VALUE); // inclusive vector min
      final double maxIn = v.isCategorical() ? v.domain().length-1 : Math.min(v.max(), Double.MAX_VALUE); // inclusive vector max
      final double maxEx = v.isCategorical() ? v.domain().length : find_maxEx(maxIn,v.isInt()?1:0);     // smallest exclusive max
      final long vlen = v.length();
      final long nacnt = v.naCnt();
      try {
        byte type = (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0));
        boolean intOpt = useIntOpt(v, parms, cs);
        hs[c] = nacnt == vlen || v.isConst(true) ?
            null : make(fr._names[c], nbins, type, minIn, maxEx, intOpt, nacnt > 0, seed, parms, globalQuantilesKey[c], cs, checkFloatSplits);
      } catch(StepOutOfRangeException e) {
        hs[c] = null;
        LOG.warn("Column " + fr._names[c]  + " with min = " + v.min() + ", max = " + v.max() + " has step out of range (" + e.getMessage() + ") and is ignored.");
      }
      assert (hs[c] == null || vlen > 0);
    }
    return hs;
  }
  
  public static DHistogram make(String name, final int nbins, byte isInt, double min, double maxEx, boolean intOpt, boolean hasNAs, 
                                long seed, SharedTreeModel.SharedTreeParameters parms, Key globalQuantilesKey, 
                                Constraints cs, boolean checkFloatSplits) {
    UpliftDRFModel.UpliftDRFParameters.UpliftMetricType upliftMetricType = null;
    boolean useUplift = false;
    if (parms instanceof UpliftDRFModel.UpliftDRFParameters) {
      upliftMetricType = ((UpliftDRFModel.UpliftDRFParameters) parms)._uplift_metric;
      useUplift = true;
    }
    return new DHistogram(name, nbins, parms._nbins_cats, isInt, min, maxEx, intOpt, hasNAs,
            parms._min_split_improvement, parms._histogram_type, seed, globalQuantilesKey, cs, checkFloatSplits, useUplift, upliftMetricType);
  }

  /**
   * Determines if histogram making can use integer optimization when extracting data.
   * 
   * @param v input Vec
   * @param parms algo params
   * @param cs constraints specification
   * @return can we use integer representation for extracted data? 
   */
  public static boolean useIntOpt(Vec v, SharedTreeModel.SharedTreeParameters parms, Constraints cs) {
    if (cs != null) {
      // if constraints are not handled on the optimized path to avoid higher code complexity
      // (the benefit of this optimization would be small anyway as constraints introduce overhead)
      return false;
    }
    if (parms._histogram_type != HistogramType.AUTO &&
            parms._histogram_type != HistogramType.UniformAdaptive) {
      // we cannot handle non-integer split points in fast-binning
      return false;
    }
    if (v.isCategorical()) {
      // small cardinality categoricals are optimized (default for nbin_cats is 1024)
      return v.domain().length < parms._nbins_cats;
    } else if (v.isInt()) {
      double min = v.min();
      double max = v.max();
      double intLen = max - min; // length of the interval
      // only if we can fit any value in the smallest number of bins could make (nbins, 20 is default)
      return intLen < parms._nbins &&
              (int) max - (int) min == (int) intLen; // make sure the math can be done in integer domain without overflow
    }
    return false;
  }

  // Pretty-print a histogram
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(_name).append(":").append(_min).append("-").append(_maxEx).append(" step=" + (1 / _step) + " nbins=" + nbins() + " actNBins=" + actNBins() + " isInt=" + _isInt);
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
   * Compute the sample variance within a given bin
   * @param b bin id
   * @return sample variance (>= 0)
   */
  public double var(int b) {
    double n = w(b);
    if( n<=1 ) return 0;
    return Math.max(0, (wYY(b) - wY(b)* wY(b)/n)/(n-1)); //not strictly consistent with what is done elsewhere (use n instead of n-1 to get there)
  }

  void updateHisto(double[] ws, double[] resp, Object cs, double[] ys, double[] preds, int[] rows, int hi, int lo, double[] treatment){
    if (_intOpt)
      updateHistoInt(ws, (int[])cs, ys, rows, hi, lo);
    else
      updateHisto(ws, resp, (double[]) cs, ys, preds, rows, hi, lo, treatment);
  }

  /**
   * Update counts in appropriate bins. Not thread safe, assumed to have private copy.
   * 
   * NOTE: Any changes to this method need to be also made in the integer version of this function
   * (updateHistoInt).
   * 
   * @param ws observation weights
   * @param resp original response (response column of the outer model, needed to calculate Gamma denominator) 
   * @param cs column data
   * @param ys response column of the regression tree (eg. GBM residuals, not the original model response!)
   * @param preds current model predictions (optional, provided only if needed)
   * @param rows rows sorted by leaf assignemnt
   * @param hi  upper bound on index into rows array to be processed by this call (exclusive)
   * @param lo  lower bound on index into rows array to be processed by this call (inclusive)
   * @param treatment treatment column data           
   */
  void updateHisto(double[] ws, double resp[], double[] cs, double[] ys, double[] preds, int[] rows, int hi, int lo, double[] treatment){
    // Gather all the data for this set of rows, for 1 column and 1 split/NID
    // Gather min/max, wY and sum-squares.
    
    for(int r = lo; r< hi; ++r) {
      final int k = rows[r];
      final double weight = ws == null ? 1 : ws[k];
      if (weight == 0)
        continue; // Needed for DRF only
      final double col_data = cs[k];
      if (col_data < _min2) _min2 = col_data;
      if (col_data > _maxIn) _maxIn = col_data;
      final double y = ys[r]; // uses absolute indexing, ys is optimized for sequential access
      // these assertions hold for GBM, but not for DRF 
      // assert weight != 0 || y == 0;
      // assert !Double.isNaN(y);
      double wy = weight * y;
      double wyy = wy * y;
      int b = bin(col_data);
      final int binDimStart = _vals_dim * b;
      _vals[binDimStart + 0] += weight;
      _vals[binDimStart + 1] += wy;
      _vals[binDimStart + 2] += wyy;
      if (_vals_dim >= 5 && !Double.isNaN(resp[k])) {
        if (_dist._family.equals(DistributionFamily.quantile)) {
          _vals[binDimStart + 3] += _dist.deviance(weight, y, _pred1);
          _vals[binDimStart + 4] += _dist.deviance(weight, y, _pred2);
        } else {
          _vals[binDimStart + 3] += weight * (_pred1 - y) * (_pred1 - y);
          _vals[binDimStart + 4] += weight * (_pred2 - y) * (_pred2 - y);
        }
        if (_vals_dim >= 6) {
          _vals[binDimStart + 5] += _dist.gammaDenom(weight, resp[k], y, preds[k]);
          if (_vals_dim == 7) {
            _vals[binDimStart + 6] += _dist.gammaNum(weight, resp[k], y, preds[k]);
          }
        }
      }
      if(_useUplift) {
        // Note: Only for binomial, response should be (0, 1)
        double t = treatment[k];
        double rs = resp[k];
        int binDimStartUplift = _valsDimUplift * b;
        _valsUplift[binDimStartUplift] += t;                 // treatment number
        _valsUplift[binDimStartUplift + 1] += t * rs;        // treatment number with response == 1 
        _valsUplift[binDimStartUplift + 2] += (1 - t);       // control number
        _valsUplift[binDimStartUplift + 3] += (1 - t) * rs;  // control number with response == 1
      } 
    }
  }

  /**
   * This is an integer version of method updateHisto - optimized for handling small
   * positive integer numbers and low-cardinality categoricals.
   * 
   * NOTE: Any changes to this method need to be also made in the original updateHisto
   * function.
   * 
   * @param ws optional vector of weights, indexed indirectly using rows indices
   * @param cs chunk data, indexed indirectly using rows indices
   * @param ys targets, uses absolute indexing - maintains data co-locality and optimized for sequential access
   * @param rows row indices
   * @param hi upper boundary in rows array (exclusive)
   * @param lo lower boundary in rows array (inclusive)
   */
  void updateHistoInt(double[] ws, int[] cs, double[] ys, 
                      int[] rows, final int hi, final int lo){
    // min2_int/maxIn_int integer version of the boundaries, only cast once - only int ops within the loop
    int min2_int = _min2 == Double.MAX_VALUE ? Integer.MAX_VALUE : (int) _min2;
    int maxIn_int = _maxIn == -Double.MIN_VALUE ? Integer.MIN_VALUE : (int) _maxIn;

    for(int r = lo; r < hi; r++) {
      final int k = rows[r];
      final double weight = ws == null ? 1 : ws[k];
      if (weight == 0)
        continue; // Needed for DRF only
      final int col_data = cs[k];
      final int b;
      if (col_data != INT_NA) {
        if (col_data < min2_int) min2_int = col_data;
        if (col_data > maxIn_int) maxIn_int = col_data;
        b = col_data - _minInt;
      } else 
        b = _nbin;
      final double y = ys[r]; // uses absolute indexing, ys is optimized for sequential access
      double wy = weight * y;
      double wyy = wy * y;
      final int binDimStart = _vals_dim*b;
      _vals[binDimStart + 0] += weight;
      _vals[binDimStart + 1] += wy;
      _vals[binDimStart + 2] += wyy;
    }

    _min2 = min2_int;
    _maxIn = maxIn_int;
  }

  /**
   * Extracts data from a chunk into a structure that is optimized for given column type
   * 
   * @param chk input chunk
   * @param cache optional - already existing instance of the cache
   * @param len length of the data
   * @param maxChunkSz maximum chunk size on the local node, will determine the size of the cache
   * @return extracted data
   */
  Object extractData(Chunk chk, Object cache, int len, int maxChunkSz) {
    if (cache == null) {
      if (_intOpt) {
        cache = MemoryManager.malloc4(maxChunkSz);
      } else {
        cache = MemoryManager.malloc8d(maxChunkSz);
      }
    }
    if (_intOpt)
      chk.getIntegers((int[])cache, 0, len, INT_NA);
    else
      chk.getDoubles((double[])cache, 0, len);
    return cache;
  }

  /**
   * Cast bin values (except for sums of weights) to floats to drop least significant bits.
   * Improves reproducibility (drop bits most affected by floating point error).
   */
  public void reducePrecision(){
    if(_vals == null) return;
    for(int i = 0; i < _vals.length; i+=_vals_dim) {
      _vals[i+1] = (float)_vals[i+1];
      _vals[i+2] = (float)_vals[i+2];
    }
  }
  
  static double[] makeRandomSplitPoints(int nbin, Random rng) {
    final double[] splitPts = new double[nbin];
    splitPts[0] = 0;
    for (int i = 1; i < nbin; i++)
      splitPts[i] = rng.nextFloat() * nbin;
    Arrays.sort(splitPts);
    return splitPts;
  }
}
