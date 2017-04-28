package ai.h2o.automl.colmeta;

import hex.tree.DHistogram;
import water.Iced;
import water.fvec.Vec;
import java.util.HashMap;

/** Column Meta Data
 *
 * Holds usual rollup stats and additional interesting bits of information.
 */
public class ColMeta extends Iced {
  public static final String[] METAVALUES = new String[]{
          "idFrame", "ColumnName", "ColumnType", "Min", "Max", "Mean", "Median",
          "Variance", "Cardinality", "Kurtosis", "Skew", "VIF", "FractionNA",
          "TimeToMRTaskMillis"};

  //private static transient Set<Class<? extends Guesser>> _guesserClasses;
  private static transient String[] _guessers;

  public final Vec _v;          // the column
  public byte _nameType;  // guessed at by ColNameScanner
  public final String _name;    // column name
  public final int _idx;        // index into the input frame from automl
  public boolean _ignored;      // should this column be ignored outright
  public boolean _response;     // is this a response column?
  public double _percentNA;     // fraction of NAs in the column
  public double _variance;      // variance of the column, pulled from the vec
  public double _sigma;         // pulled from vec rollups
  public boolean _stratify;     // do stratified sampling when building weight columns
  public double[] _dist;        // distribution of classes
  public double[] _weightMult;  // weight multipliers for each class to even out distributions

  public boolean _isNumeric;    // is this a numeric column
  public boolean _isCategorical; // is this a categorical column

  public static final double SQLNAN = -99999;
  public boolean _isClass;      // is a classification problem, only valid to ask when _response is true
  public boolean isClassification() {
    if( _response ) return _isClass;
    throw new IllegalArgumentException("Cannot ask non-response metadata if problem is classification");
  }

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
  public double _thirdMoment;   // used for skew/kurtosis; NaN if not numeric
  public double _fourthMoment;  // used for skew/kurtosis; NaN if not numeric
  public double _kurtosis;      // the sharpness of the peak of a frequency-distribution curve
  public double _skew;          // measure of the assymetry of a distribution; < 0 means shifted to the right; > 0 means shifted to the left
  public int _cardinality;                // length of domain

  // VIF
  public double _vif;           // vifs computed by FrameMeta

  // SECOND PASS
  // https://0xdata.atlassian.net/browse/STEAM-41 --column metadata to gather
  public long _numUniques;
  public double _avgUniquesPerChunk;   // number of uniques per chunk divided by number of chunks

  public boolean  _chunksMonotonicallyIncreasing;  // indicates some weak ordering in the dataset (by this column)
  public double[] _chunkBoundaries;                     // boundary values for each chunk [c1.min, c1.max, c2.min, c2.max, ...]; only for numeric vecs

  public double _median;

  // is this an ID for IID data?
  //   - all unique
  //   - increasing ints
  public boolean _isRowBasedId;

  // is this an ID for non-IID data, like timeseries?
  public boolean _isNonIidId;

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
    //if(_ignored) _ignoredReason = IgnoreReason.user_specified;
    _idx = idx;
    //_nameType=ColNameScanner.IGNORED;
    _response = response;
    _percentNA = (double) v.naCnt() / (double) v.length();
    _sigma = v.sigma();
    _variance = _sigma * _sigma;
    _vif = -1;
    if(v.isNumeric() && !ignored && !response){
      _isNumeric =true;
    }else{
      _isNumeric =false;
    }
    if(v.isCategorical() && !ignored && !response){
      _isCategorical =true;
    }else{
      _isCategorical =false;
    }

  }

  public ColMeta(Vec v, String colname, int idx, boolean response) {
    this(v,colname,idx,response,false);
  }

    public static HashMap<String, Object> makeEmptyColMeta() {
    HashMap<String,Object> hm = new HashMap<>();
    for(String key: ColMeta.METAVALUES) hm.put(key,null);
    return hm;
  }

  // TODO: enum, please!
  public String selectBasicTransform() {
    if( _ignored )                 return "ignored";
    if( _v.isBinary() )            return "none";
    if( _v.isTime() || _isDate )   return "time";  // actually we have a time/date column, so apply some time transforms
    if( _v.max() - _v.min() > 1e4) return "log";   // take a log if spans more than 2 orders
    if( _v.isNumeric() && !_v.isInt() ) return "recip"; // try the reciprocal!
    return "none";                                 // no transform if not interesting
  }

  public void fillColMeta(HashMap<String, Object> cm, int idFrame) {
    cm.put("idFrame", idFrame);
    cm.put("ColumnName", _name);
    cm.put("ColumnType", _v.get_type_str()); // TODO:
    if( !_v.isNumeric() ) {
      cm.put("Min", SQLNAN);
      cm.put("Max", SQLNAN);
      cm.put("Mean", SQLNAN);
      cm.put("Median", SQLNAN);
      cm.put("Variance", SQLNAN);
      cm.put("Cardinality", _v.cardinality());
      cm.put("Kurtosis", SQLNAN);
      cm.put("Skew", SQLNAN);
      cm.put("VIF", SQLNAN);
    } else {
      cm.put("Min", _v.min());
      cm.put("Max", _v.max());
      cm.put("Mean", _v.mean());
      cm.put("Median", _v.pctiles()[8/*p=0.5 pctile; see Vec.PERCENTILES*/]);
      cm.put("Variance", _v.sigma()*_v.sigma());
      cm.put("Cardinality", SQLNAN);
      cm.put("Kurtosis", _kurtosis);
      cm.put("Skew", _skew);
      cm.put("VIF", _vif);
    }
    cm.put("FractionNA", (double) _v.naCnt() / (double) _v.length() );
    cm.put("TimeToMRTaskMillis", _MRTaskMillis);
  }
}
