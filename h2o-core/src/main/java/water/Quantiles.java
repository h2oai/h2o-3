package water;

import water.api.API;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.Categorical;
import water.util.Log;

/**
* Created by rpeck on 11/3/14.
*/
public final class Quantiles extends Iced {

  // IN
  public Frame _source_key;
  public Vec _column;
  public double _quantile = 0.5;
  public int _max_qbins = 1000;
  public int _multiple_pass = 1;
  public int _interpolation_type = 7;
  public int _max_ncols = 1000;

  // OUT
  public String _column_name;
  public double _quantile_requested;
  public int _interpolation_type_used;
  public boolean   _interpolated = false; // FIX! do I need this?
  public int _iterations;
  public double _result;
  public double _result_single;


  public static final int MAX_ENUM_SIZE = Categorical.MAX_ENUM_SIZE;

  public long      _totalRows;    // non-empty rows per group
  // FIX! not sure if I need to save these here from vec
  // why were these 'transient' ? doesn't make sense if hcnt2 stuff wasn't transient
  // they're not very big. are they serialized in the map/reduce?
  public double     _max;
  public double     _min;
  public boolean    _isInt;
  public boolean    _isEnum;
  public String[]   _domain;

  // used to feed the next iteration for multipass?
  // used in exactQuantilesMultiPass only
  public double     _valStart;
  public double     _valEnd;
  public long       _valMaxBinCnt;

  // just for info on current pass?
  public double    _valRange;
  public double    _valBinSize;

  public double    _newValStart;
  public double    _newValEnd;
  public double[]  _pctile;
  public boolean   _done = false; // FIX! do I need this?

  // OUTPUTS
  // Basic info
  @API(help="name"    ) public String colname; // FIX! currently not set. Need at least one for class loading

  public long[]  hcnt2; // finer histogram. not visible
  public double[]  hcnt2_min; // min actual for each bin
  public double[]  hcnt2_max; // max actual for each bin
  public long  hcnt2_low; // count below current binning
  public long  hcnt2_high; // count above current binning
  public double hcnt2_high_min; // min above current binning

  public static class BinningTask extends MRTask<BinningTask> {
    private final int _max_qbins;
    private final double _valStart;
    private final double _valEnd;

    public Quantiles _qbins[];

    public BinningTask(int max_qbins, double valStart, double valEnd) {
      _max_qbins = max_qbins;
      _valStart = valStart;
      _valEnd = valEnd;
    }

    @Override public void map(Chunk[] cs) {
      _qbins = new Quantiles[cs.length];
      for (int i = 0; i < cs.length; i++)
        _qbins[i] = new Quantiles().setVecFields(_fr.vecs()[i], _max_qbins, _valStart, _valEnd).add(cs[i]);
    }

    @Override public void reduce(BinningTask other) {
      for (int i = 0; i < _qbins.length; i++)
        _qbins[i].add(other._qbins[i]);
      // will all the map memory get reclaimed now, since the reduce has gathered it?
      // we want to keep 1st iteration object around in for lists of thresholds to do
      // so hopefully this means just the reduce histogram will stay around.
      // FIX! Maybe unnecesary/implied or better way?
      other = null;
    }
  }

  // FIX! currently only take one quantile at a time here..ability to do a list though
  public void finishUp(Vec vec, double[] quantiles_to_do, int interpolation_type, boolean multiPass) {
    assert quantiles_to_do.length == 1 : "currently one quantile at a time. caller can reuse qbin for now.";
    // below, we force it to ignore length and only do [0]
    // need to figure out if we need to do a list and how that's returned
    _pctile = new double[quantiles_to_do.length];
    if ( _isEnum ) {
      _done = false;
    }
    else {
      if ( multiPass ) {
        _done = exactQuantilesMultiPass(_pctile, quantiles_to_do, interpolation_type);
      }
      else {
        _done = approxQuantilesOnePass(_pctile, quantiles_to_do, interpolation_type);
      }
    }
  }

  public Quantiles() { }

  public Quantiles(Vec vec, int max_qbins, double valStart, double valEnd) {
    this.setVecFields(vec, max_qbins, valStart, valEnd);
  }

  public Quantiles(Vec vec) {
    // default to 1000 bin
    // still would need to call the finishUp you want, to get a result,
    // and do multipass iteration/finishUp, if desired
    this(vec, 1000, vec.min(), vec.max());
  }

  public Quantiles(Vec vec, Frame key, double q, int max_q, int multi_pass,
                   int interpo_type, int max_nc, String col_name, double requested_quantile, int interpo_type_used,
                   boolean interpoed, int iters, double res, double res_single) {
    this.setAllFields(vec, key, q, max_q, multi_pass, interpo_type, max_nc, col_name, requested_quantile, interpo_type_used, interpoed, iters, res, res_single);
  }

  public Quantiles setVecFields(Vec vec, int max_qbins, double valStart, double valEnd) {
    _isEnum = vec.isEnum();
    _isInt = vec.isInt();
    _domain = vec.isEnum() ? vec.domain() : null;
    _max = vec.max();
    _min = vec.min();

    _totalRows = 0;
    _valStart = valStart;
    _valEnd = valEnd;
    _valRange = valEnd - valStart;

    assert max_qbins > 0 && max_qbins <= 1000000 : "max_qbins must be >0 and <= 1000000";
    int desiredBinCnt = max_qbins;
    int maxBinCnt = desiredBinCnt + 1;
    _valBinSize = _valRange / (desiredBinCnt + 0.0);
    _valMaxBinCnt = maxBinCnt;

    if( vec.isEnum() && _domain.length < MAX_ENUM_SIZE ) {
      hcnt2 = new long[_domain.length];
      hcnt2_min = new double[_domain.length];
      hcnt2_max = new double[_domain.length];
    }
    else if ( !Double.isNaN(_min) ) {
      assert maxBinCnt > 0;
      // Log.debug("Q_ Multiple pass histogram starts at "+_valStart);
      // Log.debug("Q_ _min "+_min+" _max "+_max);
      hcnt2 = new long[maxBinCnt];
      hcnt2_min = new double[maxBinCnt];
      hcnt2_max = new double[maxBinCnt];
    }
    else { // vec does not contain finite numbers
      // okay this one entry hcnt2 stuff is making the algo die ( I guess the min was nan above)
      // for now, just make it length 2
      hcnt2 = new long[2];
      hcnt2_min = new double[2];
      hcnt2_max = new double[2];
    }
    hcnt2_low = 0;
    hcnt2_high = 0;
    hcnt2_high_min = 0;
    // hcnt2 implicitly zeroed on new
    return this;
  }

  public Quantiles setAllFields(Vec vec, Frame key, double q, int max_q, int multi_pass,
                              int interpo_type, int max_nc, String col_name, double requested_quantile, int interpo_type_used,
                              boolean interpoed, int iters, double res, double res_single) {
    this.setVecFields(vec, 1000, vec.min(), vec.max());

    // IN
    _source_key = key;
    _column = vec;
    _quantile = q;
    _max_qbins = max_q;
    _multiple_pass = multi_pass;
    _interpolation_type = interpo_type;
    _max_ncols = max_nc;

    // OUT
    _column_name = col_name;
    _quantile_requested = requested_quantile;
    _interpolation_type_used = interpo_type_used;
    _interpolated = interpoed;
    _iterations = iters;
    _result = res;
    _result_single = res_single;
    return this;
  }

  public Quantiles add(Chunk chk) {
    for (int i = 0; i < chk._len; i++)
      add(chk.at0(i));
    return this;
  }
  public void add(double val) {
    if ( Double.isNaN(val) ) return;
    // can get infinity due to bad enum parse to real
    // histogram is sized ok, but the index calc below will be too big
    // just drop them. not sure if something better to do?
    if( val==Double.POSITIVE_INFINITY ) return;
    if( val==Double.NEGATIVE_INFINITY ) return;
    if ( _isEnum ) return;

    _totalRows++;
    long maxBinCnt = _valMaxBinCnt;

    // multi pass exact. Should be able to do this for both, if the valStart param is correct
    long binIdx2;
    // Need to count the stuff outside the bin-gathering,
    // since threshold compare is based on total row compare
    double valOffset = val - _valStart;

    // FIX! do we really need this special case? Not hurting.
    if (hcnt2.length==1) {
      binIdx2 = 0;
    }
    else {
      binIdx2 = (int) Math.floor(valOffset / _valBinSize);
    }
    int binIdx2Int = (int) binIdx2;

    // we always need the start condition in the bins?
    // maybe some redundancy in two compares
    if ( valOffset < 0 || binIdx2Int<0 ) {
      ++hcnt2_low;
    }
    // we always need the end condition in the bins?
    // would using valOffset here be less accurate? maybe some redundancy in two compares
    // can't use maxBinCnt-1, because the extra bin is used for one value (the bounds)
    else if ( val > _valEnd || binIdx2>=maxBinCnt ) {
      if ( (hcnt2_high==0) || (val < hcnt2_high_min) ) hcnt2_high_min = val;
      ++hcnt2_high;
    }
    else {
      assert (binIdx2Int >= 0 && binIdx2Int < hcnt2.length) :
              "binIdx2Int too big for hcnt2 "+binIdx2Int+" "+hcnt2.length;
      // Log.debug("Q_ val: "+val+" valOffset: "+valOffset+" _valBinSize: "+_valBinSize);
      assert (binIdx2Int>=0) && (binIdx2Int<=maxBinCnt) : "binIdx2Int "+binIdx2Int+" out of range";

      if ( hcnt2[binIdx2Int]==0 || (val < hcnt2_min[binIdx2Int]) ) hcnt2_min[binIdx2Int] = val;
      if ( hcnt2[binIdx2Int]==0 || (val > hcnt2_max[binIdx2Int]) ) hcnt2_max[binIdx2Int] = val;
      ++hcnt2[binIdx2Int];

      // For debug/info, can report when it goes into extra bin.
      // is it ever due to fp arith? Or just the max value?
      // not an error! should be protected by newValEnd below, and nextK
      // estimates should go into the extra bin if interpolation is needed
      if ( false && (binIdx2 == (maxBinCnt-1)) ) {
        Log.debug("\nQ_ FP! val went into the extra maxBinCnt bin:" +
                binIdx2 + " " + hcnt2_high_min + " " + valOffset + " " +
                val + " " + _valStart + " " + hcnt2_high + " " + val + " " + _valEnd, "\n");
      }
    }
  }

  public Quantiles add(Quantiles other) {
    if ( _isEnum ) return this;

    assert !Double.isNaN(other._totalRows) : "NaN in other._totalRows merging";
    assert !Double.isNaN(_totalRows) : "NaN in _totalRows merging";
    _totalRows += other._totalRows;

    // merge hcnt2 per-bin mins
    // other must be same length, but use it's length for safety
    // could add assert on lengths?
    for (int k = 0; k < other.hcnt2_min.length; k++) {
      // Shouldn't get any
      assert !Double.isNaN(other.hcnt2_min[k]) : "NaN in other.hcnt2_min merging";
      assert !Double.isNaN(other.hcnt2[k]) : "NaN in hcnt2_min merging";
      assert !Double.isNaN(hcnt2_min[k]) : "NaN in hcnt2_min merging";
      assert !Double.isNaN(hcnt2[k]) : "NaN in hcnt2_min merging";

      // cover the initial case (relying on initial min = 0 to work is wrong)
      // Only take the new max if it's hcnt2 is non-zero. like a valid bit
      // can hcnt2 ever be null here?
      if (other.hcnt2[k] > 0) {
        if ( hcnt2[k]==0 || ( other.hcnt2_min[k] < hcnt2_min[k] )) {
          hcnt2_min[k] = other.hcnt2_min[k];
        }
      }
    }

    // merge hcnt2 per-bin maxs
    // other must be same length, but use it's length for safety
    for (int k = 0; k < other.hcnt2_max.length; k++) {
      // shouldn't get any
      assert !Double.isNaN(other.hcnt2_max[k]) : "NaN in other.hcnt2_max merging";
      assert !Double.isNaN(other.hcnt2[k]) : "NaN in hcnt2_min merging";
      assert !Double.isNaN(hcnt2_max[k]) : "NaN in hcnt2_max merging";
      assert !Double.isNaN(hcnt2[k]) : "NaN in hcnt2_max merging";

      // cover the initial case (relying on initial min = 0 to work is wrong)
      // Only take the new max if it's hcnt2 is non-zero. like a valid bit
      // can hcnt2 ever be null here?
      if (other.hcnt2[k] > 0) {
        if ( hcnt2[k]==0 || ( other.hcnt2_max[k] > hcnt2_max[k] )) {
          hcnt2_max[k] = other.hcnt2_max[k];
        }
      }
    }

    // 3 new things to merge for multipass histgrams (counts above/below the bins, and the min above the bins)
    assert !Double.isNaN(other.hcnt2_high) : "NaN in other.hcnt2_high merging";
    assert !Double.isNaN(other.hcnt2_low) : "NaN in other.hcnt2_low merging";
    assert !Double.isNaN(hcnt2_high) : "NaN in hcnt2_high merging";
    assert !Double.isNaN(hcnt2_low) : "NaN in hcnt2_low merging";
    assert other.hcnt2_high==0 || !Double.isNaN(other.hcnt2_high_min) : "0 or NaN in hcnt2_high_min merging";

    // these are count merges
    hcnt2_low = hcnt2_low + other.hcnt2_low;
    hcnt2_high = hcnt2_high + other.hcnt2_high;

    // hcnt2_high_min validity is hcnt2_high!=0 (count)
    if (other.hcnt2_high > 0) {
      if ( hcnt2_high==0 || ( other.hcnt2_high_min < hcnt2_high_min )) {
        hcnt2_high_min = other.hcnt2_high_min;
      }
    }

    // can hcnt2 ever be null here?. Inc last, so the zero case is detected above
    // seems like everything would fail if hcnt2 doesn't exist here
    assert hcnt2 != null;
    water.util.ArrayUtils.add(hcnt2, other.hcnt2);
    return this;
  }

  // need to count >4B rows
  private long htot2(long low, long high) {
    long cnt = 0;
    for (int i = 0; i < hcnt2.length; i++) cnt+=hcnt2[i];
    // add the stuff outside the bins, 0,0 for single pass
    cnt = cnt + low + high;
    return cnt;
  }

  private boolean exactQuantilesMultiPass(double[] qtiles, double[] quantiles_to_do, int interpolation_type) {

    // looked at outside this method. setup for all NA or empty case
    // done could be the return value, really should make these 3 available differently
    // qtiles is an array just in case we support iterating on quantiles_to_do
    // but that would only work for approx, since we won't redo bins here.
    boolean done = false;
    boolean is_interpolated = false;
    qtiles[0] =  Double.NaN;

    if( hcnt2.length < 2 ) return false;
    assert !_isEnum;

    if ( _totalRows==0 ) return false;
    assert _totalRows >=0 : _totalRows;

    double newValStart = Double.NaN;
    double newValEnd = Double.NaN;
    double newValRange = Double.NaN;
    double newValBinSize = Double.NaN;

    boolean forceBestApprox = interpolation_type==-1;

    long newValLowCnt;
    long maxBinCnt = _valMaxBinCnt;
    assert maxBinCnt>1;
    long desiredBinCnt = maxBinCnt - 1;

    double threshold = quantiles_to_do[0];

    assert _valEnd!=Double.NaN : _valEnd;
    assert _valStart!=Double.NaN : _valStart;
    assert _valBinSize!=Double.NaN : _valBinSize;
    if ( _valStart==_valEnd ) Log.debug("exactQuantilesMultiPass: start/end are equal. "+_valStart+" "+_valEnd);
    else assert (_valBinSize!=0 && _valBinSize!=Double.NaN) : _valBinSize;

    //  everything should either be in low, the bins, or high
    long totalBinnedRows = htot2(hcnt2_low, hcnt2_high);
    Log.debug("Q_ totalRows check: "+_totalRows+" "+totalBinnedRows+" "+hcnt2_low+" "+hcnt2_high+" "+_valStart+" "+_valEnd);
    assert _totalRows==totalBinnedRows : _totalRows+" "+totalBinnedRows+" "+hcnt2_low+" "+hcnt2_high;

    // Find the row count we want to hit, within some bin.
    long currentCnt = hcnt2_low;
    double targetCntFull = threshold * (_totalRows-1);  //  zero based indexing
    long targetCntInt = (long) Math.floor(targetCntFull);
    double targetCntFract = targetCntFull  - (double) targetCntInt;
    assert (targetCntFract>=0) && (targetCntFract<=1);
    Log.debug("Q_ targetCntInt: "+targetCntInt+" targetCntFract: "+targetCntFract);

    // walk thru and find out what bin to look inside
    int k = 0;
    while(k!=maxBinCnt && ((currentCnt + hcnt2[k]) <= targetCntInt)) {
      // Log.debug("Q_ Looping for k: "+threshold+" "+k+" "+maxBinCnt+" "+currentCnt+" "+targetCntInt+
      //   " "+hcnt2[k]+" "+hcnt2_min[k]+" "+hcnt2_max[k]);
      currentCnt += hcnt2[k];
      ++k;
      // Note the loop condition covers the breakout condition:
      // (currentCnt==targetCntInt && (hcnt2[k]!=0)
      // also: don't go pass array bounds
    }

    Log.debug("Q_ Found k: "+threshold+" "+k+" "+currentCnt+" "+targetCntInt+
            " "+_totalRows+" "+hcnt2[k]+" "+hcnt2_min[k]+" "+hcnt2_max[k]);

    assert (currentCnt + hcnt2[k]) > targetCntInt : targetCntInt+" "+currentCnt+" "+k+" "+" "+maxBinCnt;
    assert hcnt2[k]!=1 || hcnt2_min[k]==hcnt2_max[k];

    // Do mean and linear interpolation, if we don't land on a row
    // WATCH OUT when comparing results if linear interpolation...it's dependent on
    // the number of rows in the dataset, not just adjacent values. So if you skipped a row
    // for some reason (header guess?) in a comparison tool, you can get small errors
    // both type 2 and type 7 give exact answers that match alternate tools
    // (if they do type 2 and 7). scklearn doesn't do type 2 but does do type 7
    // (but not by default in mquantiles())

    // the linear interpolation for k between row a (vala) and row b (valb) is
    //    pctDiff = (k-a)/(b-a)
    //    dDiff = pctDiff * (valb - vala)
    //    result = vala + dDiff

    double guess = Double.NaN;
    double pctDiff, dDiff;
    // -1 is for single pass approximation
    assert (interpolation_type==2) || (interpolation_type==7) || (interpolation_type==-1): "Unsupported type "+interpolation_type;

    // special cases. If the desired row is the last of equal values in this bin (2 or more)
    // we will need to intepolate with a nextK out-of-bin value
    // we can't iterate, since it won't improve things and the bin-size will be zero!
    // trying to resolve case of binsize=0 for next pass, after this, is flawed thinking.
    // implies the values are not the same..end of bin interpolate to next
    boolean atStartOfBin = hcnt2[k]>=1 && (currentCnt == targetCntInt);
    boolean atEndOfBin = !atStartOfBin && (hcnt2[k]>=2 && ((currentCnt + hcnt2[k] - 1) == targetCntInt));
    boolean inMidOfBin = !atStartOfBin && !atEndOfBin && (hcnt2[k]>=3) && (hcnt2_min[k]==hcnt2_max[k]);

    boolean interpolateEndNeeded = false;
    if ( atEndOfBin ) {
      if ( targetCntFract != 0 ) {
        interpolateEndNeeded = true;
      }
      else {
        guess = hcnt2_max[k];
        done = true;
        Log.debug("Q_ Guess M "+guess);
      }
    }
    else if ( inMidOfBin ) {
      // if we know there is something before and after us with same value,
      // we never need to interpolate (only allowed when min=max
      guess = hcnt2_min[k];
      done = true;
      Log.debug("Q_ Guess N "+guess);
    }

    if ( !done && atStartOfBin ) {
      // no interpolation needed
      if ( hcnt2[k]>2 && (hcnt2_min[k]==hcnt2_max[k]) ) {
        guess = hcnt2_min[k];
        done = true;
        Log.debug("Q_ Guess A "+guess);
      }
      // min/max can be equal or not equal here
      else if ( hcnt2[k]==2 ) { // interpolate between min/max for the two value bin
        if ( interpolation_type==2 ) { // type 2 (mean)
          guess = (hcnt2_max[k] + hcnt2_min[k]) / 2.0;
        }
        else { // default to type 7 (linear interpolation)
          // Unlike mean, which just depends on two adjacent values, this adjustment
          // adds possible errors related to the arithmetic on the total # of rows.
          dDiff = hcnt2_max[k] - hcnt2_min[k]; // two adjacent..as if sorted!
          // targetCntFract is fraction of total rows
          guess = hcnt2_min[k] + (targetCntFract * dDiff);
        }
        done = true;
        is_interpolated = true;
        Log.debug("Q_ Guess B "+guess+" with type "+interpolation_type+" targetCntFract: "+targetCntFract);
      }
      // no interpolation needed
      else if ( (hcnt2[k]==1) && (targetCntFract==0) ) {
        assert hcnt2_min[k]==hcnt2_max[k];
        guess = hcnt2_min[k];
        done = true;
        Log.debug("Q_ Guess C "+guess);
      }
    }

    // interpolate into a nextK value
    // all the qualification is so we don't set done when we're not, for multipass
    // interpolate from single bin, end of two entry bin, or for approx
    boolean stillCanGetIt = atStartOfBin && hcnt2[k]==1 && targetCntFract!=0;
    if ( !done && (stillCanGetIt || interpolateEndNeeded || forceBestApprox)) {

      if ( hcnt2[k]==1 ) {
        assert hcnt2_min[k]==hcnt2_max[k];
        Log.debug("Q_ Single value in this bin, but fractional means we need to interpolate to next non-zero");
      }
      if ( interpolateEndNeeded ) {
        Log.debug("Q_ Interpolating off the end of a bin!");
      }

      int nextK;
      if ( k<maxBinCnt ) nextK = k + 1; //  could put it over maxBinCnt
      else nextK = k;
      // definitely see stuff going into the extra bin, so search that too!
      while ( (nextK<maxBinCnt) && (hcnt2[nextK]==0) ) ++nextK;

      assert nextK > k : k+" "+nextK;
      //  have the "extra bin" for this
      double nextVal;
      if ( nextK >= maxBinCnt ) {
        // assume we didn't set hcnt2_high_min on first pass, because tighter start/end bounds
        if ( forceBestApprox ) {
          Log.debug("Q_ Using _valEnd for approx interpolate: "+_valEnd);
          nextVal = _valEnd;
        }
        else {
          assert hcnt2_high!=0;
          Log.debug("Q_ Using hcnt2_high_min for interpolate: "+hcnt2_high_min);
          nextVal = hcnt2_high_min;
        }
      }
      else {
        Log.debug("Q_ Using nextK for interpolate: "+nextK);
        assert hcnt2[nextK]!=0;
        nextVal = hcnt2_min[nextK];
      }

      Log.debug("Q_ k hcnt2_max[k] nextVal");
      Log.debug("Q_ "+k+" "+hcnt2_max[k]+" "+nextVal);
      Log.debug("Q_ \nInterpolating result using nextK: "+nextK+ " nextVal: "+nextVal);

      // type 7 (linear interpolation) ||
      // single pass approx..with unresolved bin
      if ( (forceBestApprox & stillCanGetIt) || interpolation_type==7) {
        dDiff = nextVal - hcnt2_max[k]; // two adjacent, as if sorted!
        // targetCntFract is fraction of total rows
        guess = hcnt2_max[k] + (targetCntFract * dDiff);
      }
      else if ( forceBestApprox ) { // single pass approx..with unresolved bin
        // best to use hcnt2_max[k] instead of nextVal here, to keep
        // within the guaranteed worst case error bounds
        dDiff = (hcnt2_max[k] - hcnt2_min[k]) / hcnt2[k];
        guess = hcnt2_min[k] + (targetCntFull-currentCnt) * dDiff;
      }
      else { // type 2 (mean)
        guess = (hcnt2_max[k] + nextVal) / 2.0;
      }

      is_interpolated = true;
      done = true; //  has to be one above us when needed. (or we're at end)
      Log.debug("Q_ Guess D "+guess+" with type "+interpolation_type+
              " targetCntFull: "+targetCntFull+" targetCntFract: "+targetCntFract+
              " _totalRows: " + _totalRows+" "+stillCanGetIt+" "+forceBestApprox);
    }

    if ( !done  && !forceBestApprox) { // don't need for 1 pass approx

      // Possible bin leakage at start/end edges due to fp arith.
      // bin index arith may resolve OVER the boundary created by the compare for
      // hcnt2_high compare.
      // I suppose just one value should be in desiredBinCnt+1 bin -> the end value?)

      // To cover possible fp issues:
      // See if there's a non-zero bin below (min) or above (max) you, to avoid shrinking wrong.
      // Just need to check the one bin below and above k, if they exist.
      // They might have zero entries, but then it's okay to ignore them.
      // update: use the closest edge in the next bin. better forward progress for small bin counts
      // This code may make the practical min bin count around 4 or so (not 2).
      // what has length 1 hcnt2 that makese this fail? Enums? shouldn't get here.
      newValStart = hcnt2_min[k];
      if ( k > 0 ) {
        if ( hcnt2[k-1]>0 && (hcnt2_max[k-1]<hcnt2_min[k]) ) {
          newValStart = hcnt2_max[k-1];
        }
      }

      // subtle. we do sometimes put stuff in the extra end bin (see above)
      // k might be pointing to one less than that (like k=0 for 1 bin case)
      newValEnd = hcnt2_max[k];
      if ( k < (maxBinCnt-1) )  {
        assert k+1 < hcnt2.length : k+" "+hcnt2.length+" "+_valMaxBinCnt+" "+_isEnum+" "+_isInt;
        if ( hcnt2[k+1]>0 && (hcnt2_min[k+1]>hcnt2_max[k]) ) {
          newValEnd = hcnt2_min[k+1];
        }
      }

      newValRange = newValEnd - newValStart;
      //  maxBinCnt is always binCount + 1, since we might cover over due to rounding/fp issues?
      newValBinSize = newValRange / (desiredBinCnt + 0.0);
      newValLowCnt = currentCnt - 1; // is this right? don't use for anything (debug?)

      // Since we always may need an interpolation, this seems bad if we get this with !done
      if ( newValBinSize==0 ) {
        Log.debug("Q_ Assuming done because newValBinSize is 0.");
        Log.debug("Q_ newValRange: "+newValRange+
                " hcnt2[k]: "+hcnt2[k]+
                " hcnt2_min[k]: "+hcnt2_min[k]+
                " hcnt2_max[k]: "+hcnt2_max[k]);
        guess = newValStart;
        Log.debug("Q_ Guess G "+guess);
        // maybe make this assert false, to see?
        assert true : "Should never get newValBinSize==0 in !done branch";
        done = true;
      }
    }

    Log.debug("Q_ guess: "+guess+" done: "+done+" hcnt2[k]: "+hcnt2[k]);
    Log.debug("Q_ currentCnt: "+currentCnt+" targetCntInt: "+targetCntInt+" hcnt2_low: "+hcnt2_low+" hcnt2_high: "+hcnt2_high);
    Log.debug("Q_ was "+_valStart+" "+_valEnd+" "+_valRange+" "+_valBinSize);
    Log.debug("Q_ next "+newValStart+" "+newValEnd+" "+newValRange+" "+newValBinSize);

    qtiles[0] = guess;
    // We want to leave them now! we reuse in exec for multi-thresholds
    // hcnt2 = null;
    // hcnt2_min = null;
    // hcnt2_max = null;
    _newValStart = newValStart;
    _newValEnd = newValEnd;
    _interpolated = is_interpolated;
    return done;
  }

  // this won't be used with a multipass iteration of qbins. So it alays has to return a best guess
  // Also, it needs to interpolate for bins that have different values that aren't resolved by min/max
  // so we give it a special interpolation type (-1) that we'll decode and use above
  private boolean approxQuantilesOnePass(double[] qtiles, double[] quantiles_to_do, int interpolation_type) {
    // exactQuantilesMultiPass(qtiles, quantiles_to_do, -1) ;
    exactQuantilesMultiPass(qtiles, quantiles_to_do, -1) ;
    return true;
  }
}
