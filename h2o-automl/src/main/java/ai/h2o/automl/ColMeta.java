package ai.h2o.automl;


import ai.h2o.automl.autocollect.AutoCollect;
import ai.h2o.automl.guessers.ColNameScanner;
import ai.h2o.automl.transforms.Transform;
import hex.tree.DHistogram;
import water.Iced;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

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
  public final byte _nameType;  // guessed at by ColNameScanner
  public final String _name;    // column name
  public final int _idx;        // index into the input frame from automl
  public boolean _ignored;      // should this column be ignored outright
  public boolean _response;     // is this a response column?
  public double _percentNA;     // fraction of NAs in the column
  public double _variance;      // variance of the column, pulled from the vec
  public double _sigma;         // pulled from vec rollups

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
    _sigma=v.sigma();
    _variance=_sigma*_sigma;
    _vif=-1;
  }

  public static HashMap<String, Object> makeEmptyColMeta() {
    HashMap<String,Object> hm = new HashMap<>();
    for(String key: ColMeta.METAVALUES) hm.put(key,null);
    return hm;
  }

  public String selectBasicTransform() {
    if( _ignored ) return "ignored";
    if( _v.isBinary() ) return "none";
    if( _v.isTime() || _isDate ) return "time";  // actually we have a time/date column, so apply some time transforms
    if( _v.max() - _v.min() > 2e10) return "log";  // take a log if spans more than 2 orders
    return Transform.basicOps[new Random().nextInt(Transform.basicOps.length)];  // choose a random log to just try
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

    byte _type;
    int _idx;
    public static final byte INT=0;
    public static final byte DBL=1;
    public static final byte STR=2;

    String typeToString() {
      return _type==INT ? "int" : (_type==DBL ? "double" : "String");
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
  }
}