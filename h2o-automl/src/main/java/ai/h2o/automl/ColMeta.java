package ai.h2o.automl;


import ai.h2o.automl.autocollect.AutoCollect;
import ai.h2o.automl.guessers.ColNameScanner;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.tree.DHistogram;
import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;
import water.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Column Meta Data
 *
 * Holds usual rollup stats and additional interesting bits of information.
 */
public class ColMeta extends Iced {
  public static final String[] METAVALUES = new String[]{
          "idFrame", "ColumnName", "ColumnType", "Min", "Max", "Mean", "Median",
          "Variance", "Cardinality", "Kurtosis", "Skew", "VIF", "FractionNA",
          "TimeToMRTaskMillis"};

  public final Vec _v;          // the column
  public byte _nameType;  // guessed at by ColNameScanner
  public final String _name;    // column name
  public final int _idx;        // index into the input frame from automl
  public boolean _ignored;      // should this column be ignored outright
  public boolean _response;     // is this a response column?
  public double _percentNA;     // fraction of NAs in the column
  public double _variance;      // variance of the column, pulled from the vec
  public double _sigma;         // pulled from vec rollups

  public String _ignoredReason; // was this ignored by user, or by automl

  /**
   * Meta data collected on the first pass over this column.
   *
   * This is done by computing a very coarse histogram (~500 bins) for numeric vectors,
   * and building a table of occurrences for enum/string vectors (see NB below).
   *
   * Additionally, the time it takes to MRTask over the column is collected and stored.
   * This will be helpful in understanding the time it takes to build DTree instances in
   * the tree-based models.
   *
   * NB: For any table'ing done on a vec, there's a limit of 10K uniques before a more
   * efficient (i.e., MDowle-style radix sort) scheme should be engaged.
   */

  // (discussion) need to have guardrails around data and possibly some warning or error
  // about data that doesn't fit the distribution that was trained. Will want to compare
  // histos from training and testing data to see if they "match". WTM
  public DHistogram _histo;
  public long _MRTaskMillis;
  public SpecialNA _specialNAs; // found special NAs like 9999 or -1 or 0
  public double _thirdMoment;   // used for skew/kurtosis; NaN if not numeric
  public double _fourthMoment;  // used for skew/kurtosis; NaN if not numeric
  public double _kurtosis;      // the sharpness of the peak of a frequency-distribution curve
  public double _skew;          // measure of the assymetry of a distribution; < 0 means shifted to the right; > 0 means shifted to the left

  // VIF
  public double _vif;           // vifs computed by FrameMeta

  // SECOND PASS
  // https://0xdata.atlassian.net/browse/STEAM-41 --column metadata to gather
  public long _numUniq;
  public double _avgUniqPerChk;   // number of uniques per chunk divided by number of chunks

  public boolean  _chunksMonotonicallyIncreasing;  // indicates some week ordering in the dataset (by this column)
  public double[] _chkBndries;                     // boundary values for each chunk [c1.min, c1.max, c2.min, c2.max, ...]; only for numeric vecs

  public double _median;

  // is this an ID?
  //   - all unique
  //   - increasing ints
  public boolean _isID;

  // is it a date column?
  //   - has date info (possibly a month column since values are all 1-12, or 0-11)
  //   - possibly next to other date columns?
  public boolean _isDate;

  /**
   * Build metrics against the response column for this vec.
   *
   * Four scenarios:
   *      this vec       response vec
   *  1. Categorical, Categorical        - build int[this_vec.domain()][counts_per_response_cat]
   *  2. Categorical, Numerical          - build double[this_vec.domain()][mean,sd]
   *  3. Numerical,   Categorical        - build double[response_vec.domain()][mean,sd]
   *  4. Numerical,   Numerical          - not defined, could do a stupid thing with quantile
   */

  public ColMeta(Vec v, String colname, int idx, boolean response, boolean ignored) {
    _v = v;
    _name = colname;
    _ignored=ignored;
    if(_ignored) _ignoredReason = "Ignored by Input";
    _idx = idx;
    _nameType=ColNameScanner.IGNORED;
    if( !_ignored || response ) {
      _nameType = ColNameScanner.scan(_name,v);
      _response = response;

      if( !(v instanceof TransformWrappedVec || v instanceof InteractionWrappedVec) && !_response ) {

        if( _nameType==ColNameScanner.ID ) {
          assert _v.isInt(): "ColNameScanner declared this column as ID but column not integral!";

          // need to gather up some info on number of uniques in this column
          // if the number of uniques is ~nrow then want to ignore this column
          // for smaller datasets, it's ok to MR collect the grand set of uniques to a
          // single node... but as the number of rows grows (think 100M) then this becomes
          // prohibitive. As a rule of thumb, cap out number of possible uniques to 1M
          // after which we must try something else (heuristic based on unqies/chk)

          // Assume that ignorable ID columns are >= 0.
          // Using the pigeonhole principle, we can exclude any cases where the RANGE of
          // values is less than the the number of rows.
          long nrow;
          if( (v.min() >= 0) &&  (v.max() - v.min() >= (nrow=(v.length() - v.naCnt()))) ) {
            if( nrow > 5e6 )  // for > 5M rows, use a different strategy
              _ignored = nrow-(new UniqTask().doAll(v))._size <= 10;
            else
              _ignored = new UniqTaskPerChk().doAll(v)._fracUniq >= 0.75;
          }
        }
        if( _ignored ) {
          _ignoredReason = "AutoML ignoring ID column for too many uniques";
          Log.info("AutoML ignoring " + _name + " column (Reason: ID + too many uniques)");
          return;
        }
        _specialNAs = new SpecialNA(
                _v.isNumeric()
                        ? (_v.isInt() ? SpecialNA.INT : SpecialNA.DBL)
                        : SpecialNA.STR
        ).scan(v,this);
      }
      _percentNA = (double) v.naCnt() / (double) v.length();
      if (!(v instanceof TransformWrappedVec) && !(v instanceof InteractionWrappedVec)) {
        _ignored = null!=(ignoreReason(_v)); // auto ignore from the outset
        if( _ignored ) {
          _ignoredReason = "AutoML ignoring ID column for " + ignoreReason(v);
          Log.info("AutoML ignoring " + _name + " column (Reason: "+_ignoredReason + ")");
          return;
        }
        _sigma = v.sigma();
        _variance = _sigma * _sigma;
        _vif = -1;
      }
    }
  }

  private static String ignoreReason(Vec v) {
    if( v.isBad() ) return "is BAD";
    if( v.isConst()) return "is constant";
    if( v.isString()) return "is String";
    if( v.isUUID()) return "is UUID";
    return null;
  }

  private static class UniqTask extends MRTask<UniqTask> {
    NonBlockingHashMapLong<String> _hm;
    int _size;
    @Override public void setupLocal() { _hm=new NonBlockingHashMapLong<>(); }
    @Override public void map(Chunk c) {
      for(int i=0;i<c._len;++i) _hm.putIfAbsent(c.at8(i),"");
    }
    @Override public void reduce(UniqTask t) {
      if( _hm!=t._hm ) _hm.putAll(t._hm);
      t._hm=null;
    }
    @Override public void postGlobal() { _size = _hm.size(); }
  }

  private static class UniqTaskPerChk extends MRTask<UniqTaskPerChk> {
    private double _fracUniq;
    @Override public void map(Chunk c) {
      NonBlockingHashMapLong<String> hm = new NonBlockingHashMapLong<>();
      for(int i=0;i<c._len;++i) hm.putIfAbsent(c.at8(i),"");
      _fracUniq = (double)hm.size()/(double)c._len;
    }
    @Override public void reduce(UniqTaskPerChk t) { _fracUniq+=t._fracUniq; }
    @Override public void postGlobal() { _fracUniq /= (double)_fr.anyVec().nChunks(); }
  }

  public ColMeta(Vec v, String colname, int idx, boolean response) {
    this(v,colname,idx,response,false);
  }

    public static HashMap<String, Object> makeEmptyColMeta() {
    HashMap<String,Object> hm = new HashMap<>();
    for(String key: ColMeta.METAVALUES) hm.put(key,null);
    return hm;
  }

  public String selectBasicTransform() {
    if( _ignored )                 return "ignored";
    if( _v.isBinary() )            return "none";
    if( _v.isTime() || _isDate )   return "time";  // actually we have a time/date column, so apply some time transforms
    if( _v.max() - _v.min() > 1e4) return "log";   // take a log if spans more than 2 orders
    if( _v.isNumeric() && !_v.isInt() ) return "recip"; // try the reciprocal!
    return "none";                                 // no transform if not interesting
    //Transform.basicOps[new Random().nextInt(Transform.basicOps.length)];  // choose a random log to just try
  }

  public void fillColMeta(HashMap<String, Object> cm, int idFrame) {
    cm.put("idFrame", idFrame);
    cm.put("ColumnName", _name);
    cm.put("ColumnType", _v.get_type_str()); // TODO:
    if( !_v.isNumeric() ) {
      cm.put("Min", AutoCollect.SQLNAN);
      cm.put("Max", AutoCollect.SQLNAN);
      cm.put("Mean", AutoCollect.SQLNAN);
      cm.put("Median", AutoCollect.SQLNAN);
      cm.put("Variance", AutoCollect.SQLNAN);
      cm.put("Cardinality", _v.cardinality());
      cm.put("Kurtosis", AutoCollect.SQLNAN);
      cm.put("Skew", AutoCollect.SQLNAN);
      cm.put("VIF", AutoCollect.SQLNAN);
    } else {
      cm.put("Min", _v.min());
      cm.put("Max", _v.max());
      cm.put("Mean", _v.mean());
      cm.put("Median", _v.pctiles()[8/*p=0.5 pctile; see Vec.PERCENTILES*/]);
      cm.put("Variance", _v.sigma()*_v.sigma());
      cm.put("Cardinality", AutoCollect.SQLNAN);
      cm.put("Kurtosis", _kurtosis);
      cm.put("Skew", _skew);
      cm.put("VIF", _vif);
    }
    cm.put("FractionNA", (double) _v.naCnt() / (double) _v.length() );
    cm.put("TimeToMRTaskMillis", _MRTaskMillis);
  }

  // stupid wrapper class for possibly special types of NAs; things like 999999 or -1 or 0
  // https://0xdata.atlassian.net/browse/STEAM-76
  static class SpecialNA extends Iced {
    int[] _ints;
    double[] _dbls;
    String[] _strs;

    private transient HashSet<Double> _nas;

    byte _type;
    int _idx;
    public static final byte INT=0;
    public static final byte DBL=1;
    public static final byte STR=2;

    String typeToString() {
      return _type==INT ? "int" : (_type==DBL ? "double" : "String");
    }

    @Override public String toString() {
      if( _type==INT ) return arrToString(_ints);
      if( _type==DBL ) return arrToString(_dbls);
      return ArrayUtils.toString(_strs);
    }

    private String arrToString(int[] a) {
      StringBuilder s = new StringBuilder("{");
      for(int i=0;i<_idx;++i)
        if( i!=_idx-1) s.append(a[i]).append(",");
        else s.append(a[i]);
      return s.append("}").toString();
    }

    private String arrToString(double[] a) {
      StringBuilder s = new StringBuilder("{");
      for(int i=0;i<=_idx;++i)
        if( i!=_idx) s.append(a[i]).append(",");
        else s.append(a[i]);
      return s.append("}").toString();
    }

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
      assert _type==INT : "expected " + typeToString() + "; type was int";
      synchronized (this) {
        if( _idx==_ints.length-1 )
          _ints = Arrays.copyOf(_ints, _ints.length << 1);
        _ints[_idx++]=val;
      }
    }
    public void add(double val) {
      assert _type==DBL : "expected " + typeToString() + "; type was double";
      synchronized (this) {
        if( _idx==_dbls.length )
          _dbls = Arrays.copyOf(_dbls, _dbls.length << 1);
        _dbls[_idx++]=val;
      }
    }
    public void add(String val) {
      assert _type==STR : "expected " + typeToString() + "; type was String";
      synchronized (this) {
        if( _idx==_strs.length )
          _strs = Arrays.copyOf(_strs, _strs.length << 1);
        _strs[_idx++]=val;
      }
    }

    public boolean isNA(double d) {
      if( null==_nas ) setupLocal();
      return _nas.contains(d);
    }

    private void setupLocal() {
      if( _type==STR ) throw H2O.unimpl();
      _nas=new HashSet<>();
      for(int i=0;i<_idx;++i)
        _nas.add(_type==INT?_ints[i]:_dbls[i]);
    }

    SpecialNA scan(Vec v, ColMeta cm) {
      if( _type!=STR ) {
        double median;
        cm._median = median = median(v, QuantileModel.CombineMethod.INTERPOLATE);
        double mean = v.mean();
        double skew = Math.abs(mean-median);
        if( Math.abs(skew) > 700 ) {  // strong skew => maybe odd NA encoding as large/small value
          Log.info("Found a strong skew due to possible NA encoded as integer. Median=" + median + "; mean=" + mean);
          Log.info("Checking if NAs encoded as -99999 or 99999 or some variant.");
          int l = v.pctiles().length-1;
          int min = (int)v.pctiles()[0];
          int max = (int)v.pctiles()[l];
          if( min==-9999 || min==-99999 || min==-999999 ) add(min);
          else if( max==9999 || max==99999 || max==999999 ) add(max);
        }
      }
      if( 0!=_idx ) {  // added some special NAs
        long start = System.currentTimeMillis();
        new ReplaceSpecialNATask(this).doAll(v);
        Log.info("Finished substitution in " + (System.currentTimeMillis() - start)/1000. +  "seconds");
      }
      return this;
    }

    private static class ReplaceSpecialNATask extends MRTask<ReplaceSpecialNATask> {
      private final SpecialNA _nas;
      ReplaceSpecialNATask(SpecialNA nas) {
        _nas=nas;
        Log.info("Found some special NAs: " + _nas.toString());
      }
      @Override public void setupLocal() { _nas.setupLocal(); }
      @Override public void map(Chunk c) {
        for(int i=0;i<c._len;++i)
          if( _nas.isNA(c.atd(i)) ) c.setNA(i);
      }
    }
  }

  public static double median(Frame fr, QuantileModel.CombineMethod combine_method) {
    if( fr.numCols() !=1 || !fr.anyVec().isNumeric() )
      throw new IllegalArgumentException("median only works on a single numeric column");
    // Frame needs a Key for Quantile, might not have one from rapids
    Key tk=null;
    if( fr._key == null ) { DKV.put(tk = Key.make(), fr = new Frame(tk, fr.names(), fr.vecs())); }
    // Quantiles to get the median
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._probs = new double[]{0.5};
    parms._train = fr._key;
    parms._combine_method = combine_method;
    QuantileModel q = new Quantile(parms).trainModel().get();
    double median = q._output._quantiles[0][0];
    q.delete();
    if( tk!=null ) { DKV.remove(tk); }
    return median;
  }

  static double median(Vec v, QuantileModel.CombineMethod combine_method) {
    return median(new Frame(v),combine_method);
  }
}