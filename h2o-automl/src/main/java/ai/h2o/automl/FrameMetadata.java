package ai.h2o.automl;

import ai.h2o.automl.UserFeedbackEvent.*;
import ai.h2o.automl.collectors.MetaCollector;
import ai.h2o.automl.colmeta.ColMeta;
import ai.h2o.automl.utils.AutoMLUtils;
import hex.tree.DHistogram;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.mungers.AstNaOmit;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;

import static ai.h2o.automl.utils.AutoMLUtils.intListToA;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMetadata extends Iced {
  final String _datasetName;
  public final Frame _fr;
  public int[] _catFeats;
  public int[] _numFeats;
  public int[] _intCols;
  public int[] _dblCols;
  public int[] _binaryCols;
  public int[] _intNotBinaryCols;
  public int _response;
  private boolean _isClassification;
  private String[] _ignoredCols;
  private String[] _includeCols;
  private long _naCnt=-1;  // count of nas across whole frame
  private int _numFeat=-1; // count of numerical features
  private int _catFeat=-1; // count of categorical features
  private long _nclass=-1; // number of classes if classification problem
  private double[][] _dummies=null; // dummy predictions
  public ColMeta[] _cols;
  public Vec[]  _trainTestWeight;  // weight vecs for train/test splits
  private long _featsWithNa=-1;    // count of features with nas
  private long _rowsWithNa=-1;     // count of rows with nas
  private double _minSkewness=-1;    // minimum skewness across all numeric features
  private double _maxSkewness=-1;    // maximun skewness across all numeric features
  private double _meanSkewness=-1;   // mean skewness across all numeric features
  private double _stdSkewness=-1;    // standard deviation in skewness across all numeric features
  private double _medianSkewness=-1; // median across all numeric features
  private double _minKurtosis=-1;    // minimum kurtosis across all numeric features
  private double _maxKurtosis=-1;    // maximun kurtosis across all numeric features
  private double _meanKurtosis=-1;   // mean kurtosis across all numeric features
  private double _stdKurtosis=-1;    // standard deviation in kurtosis across all numeric features
  private double _medianKurtosis=-1; // median across all numeric features
  private double _minCardinality=-1;    // minimum count of symbols across all categorical features
  private double _maxCardinality=-1;    // maximun count of symbols across all categorical features
  private double _meanCardinality=-1;   // mean count of symbols across all categorical features
  private double _stdCardinality=-1;    // standard deviation in count of symbols across all categorical features
  private double _medianCardinality=-1; // median count of symbols across all categorical features

  private UserFeedback _userFeedback;

  private AstNaOmit astNaOmit;

  public static final double SQLNAN = -99999;

  public void delete() {
    for(Vec v: _trainTestWeight)
      if( null!=v ) v.remove();
  }

  // TODO: UGH: use reflection!
  public final static String[] METAVALUES = new String[]{
    "DatasetName", "NRow", "NCol", "LogNRow", "LogNCol", "NACount", "NAFraction",
    "NumberNumericFeat", "NumberCatFeat", "RatioNumericToCatFeat", "RatioCatToNumericFeat",
    "DatasetRatio", "LogDatasetRatio", "InverseDatasetRatio", "LogInverseDatasetRatio",
    "Classification", "DummyStratMSE", "DummyStratLogLoss", "DummyMostFreqMSE",
    "DummyMostFreqLogLoss", "DummyRandomMSE", "DummyRandomLogLoss", "DummyMedianMSE",
    "DummyMeanMSE", "NClass", "FeatWithNAs","RowsWithNAs","MinSkewness","MaxSkewness",
    "MeanSkewness","StdSkewness","MedianSkewness","MinKurtosis","MaxKurtosis",
    "MeanKurtosis","StdKurtosis","MedianKurtosis", "MinCardinality","MaxCardinality",
    "MeanCardinality","StdCardinality","MedianCardinality"
  };

  // TODO: UGH: use reflection!
  public static HashMap<String, Object> makeEmptyFrameMeta() {
    HashMap<String,Object> hm = new LinkedHashMap<>(); // preserve insertion order
    for(String key: FrameMetadata.METAVALUES) hm.put(key,null);
    return hm;
  }

  // Takes empty frame meta hashmap and fills in the metadata not requiring MRTask
  // TODO: make helper functions so that it's possible to iterate over METAVALUES only
  public void fillSimpleMeta(HashMap<String, Object> fm) {
    fm.put("DatasetName", _datasetName);
    fm.put("NRow", (double)_fr.numRows());
    fm.put("NCol", (double)_fr.numCols());
    fm.put("LogNRow", Math.log((double)fm.get("NRow")));
    fm.put("LogNCol", Math.log((double)fm.get("NCol")));
    fm.put("NACount", _fr.naCount());
    fm.put("NAFraction", _fr.naFraction());
    fm.put("NumberNumericFeat", (double)numberOfNumericFeatures());
    fm.put("NumberCatFeat", (double) numberOfCategoricalFeatures());
    fm.put("RatioNumericToCatFeat", Double.isInfinite((double) fm.get("NumberCatFeat"))     ? SQLNAN : (double) fm.get("NumberNumericFeat") / (double) fm.get("NumberCatFeat"));
    fm.put("RatioCatToNumericFeat", Double.isInfinite((double) fm.get("NumberNumericFeat")) ? SQLNAN : (double) fm.get("NumberCatFeat")     / (double) fm.get("NumberNumericFeat"));
    fm.put("DatasetRatio", (double) _fr.numCols() / (double) _fr.numRows());
    fm.put("LogDatasetRatio", Math.log((double) fm.get("DatasetRatio")));
    fm.put("InverseDatasetRatio", (double)_fr.numRows() / (double) _fr.numCols() );
    fm.put("LogInverseDatasetRatio", Math.log((double)fm.get("InverseDatasetRatio")));
    fm.put("Classification", _isClassification?1:0);
    fm.put("FeatWithNAs", (double)na_FeatureCount());
    fm.put("RowsWithNAs",(double)rowsWithNa());
    fm.put("NClass",(double)nClass());
    double[] skew=skewness();
    fm.put("MinSkewness",skew[0]);
    fm.put("MaxSkewness", skew[1]);
    fm.put("MeanSkewness", skew[2]);
    fm.put("StdSkewness", skew[3]);
    fm.put("MedianSkewness", skew[4]);
    double[] kurt=kurtosis();
    fm.put("MinKurtosis",kurt[0]);
    fm.put("MaxKurtosis", kurt[1]);
    fm.put("MeanKurtosis", kurt[2]);
    fm.put("StdKurtosis", kurt[3]);
    fm.put("MedianKurtosis", kurt[4]);
    double[] sym=cardinality();
    fm.put("MinCardinality",sym[0]);
    fm.put("MaxCardinality", sym[1]);
    fm.put("MeanCardinality", sym[2]);
    fm.put("StdCardinality", sym[3]);
    fm.put("MedianCardinality", sym[4]);
  }

  /**
   * Get the non-ignored columns that are not in the filter; do not include the response.
   * @param filterThese remove these columns
   * @return an int[] of the non-ignored column indexes
   */
  public int[] diffCols(int[] filterThese) {
    HashSet<Integer> filter = new HashSet<>();
    for(int i:filterThese)filter.add(i);
    ArrayList<Integer> res = new ArrayList<>();
    for(int i=0;i<_cols.length;++i) {
      if( _cols[i]._ignored || _cols[i]._response || filter.contains(i) ) continue;
      res.add(i);
    }
    return intListToA(res);
  }

  //count of features with nas
  public long na_FeatureCount() {
    if( _featsWithNa!=-1 ) return _featsWithNa;
    long cnt=0;

    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._percentNA!=0) {
        cnt += 1;   // check if const columns along with user ignored columns are included in ignored
      }
    }
    return (_featsWithNa=cnt);
  }

  //count of rows with nas
  public long rowsWithNa() {
    if( _rowsWithNa!=-1 ) return _rowsWithNa;
    String x = String.format("(na.omit %s)", _fr._key);
    Val res = Rapids.exec(x);
    Frame f = res.getFrame();
    long cnt = _fr.numRows()  -  f.numRows();
    f.delete();
    return (_rowsWithNa=cnt);

  }

  //number of classes if classification problem
  public long nClass() {
    if( _nclass!=-1 ) return _nclass;
    if(_isClassification==true){
      long cnt=0;
      cnt = _fr.vec(_response).domain().length;
      /*for(int i=0;i<_cols.length;++i) {
      if(_cols[i]._response==true) cnt = _cols[i]._v.domain().length;
    }*/
      return(_nclass=cnt);
    }else{
      return(_nclass=0);
    }
  }

  /** Loops over numeric features to get skewness summary for the frame **/
  public double[] skewness(){
    double[] ar = new double[5];
    ar[0] = _minSkewness;
    ar[1] = _maxSkewness;
    ar[2] = _meanSkewness;
    ar[3] = _stdSkewness;
    ar[4] = _medianSkewness;
    if( _minSkewness!=-1 && _maxSkewness!=-1 && _meanSkewness!=-1 && _stdSkewness!=-1 && _medianSkewness!=-1) return ar;

    if(isAnyNumeric()){
      int ar_size = numberOfNumericFeatures();
      double[] darry = new double[ar_size];
      int ind=0;
      for(int i=0;i<_cols.length;++i) {
        if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isNumeric) {
          darry[ind] = _cols[i]._skew;
          ind +=1;
        }
      }
      Vec vd1 = dvec(darry);
      Vec[] vdd = new  Vec[1];
      vdd[0] = vd1;
      Key<Frame> key = Key.make("keyD");
      String[] names= new String[1];
      names[0] ="vd1";
      Frame dr = new Frame(key,names,vdd);
      DKV.put(dr);

      ar[0] = rapidMin(dr);
      ar[1] = rapidMax(dr);
      ar[2] = rapidMean(dr);
      ar[3] = rapidSd(dr);
      ar[4] = rapidMedian(dr);
      _minSkewness = ar[0];
      _maxSkewness = ar[1];
      _meanSkewness = ar[2];
      _stdSkewness = ar[3];
      _medianSkewness = ar[4];
      dr.remove();
    }else{
    ar[0] = Double.NaN;
    ar[1] = Double.NaN;
    ar[2] = Double.NaN;
    ar[3] = Double.NaN;
    ar[4] = Double.NaN;
    _minSkewness = Double.NaN;
    _maxSkewness = Double.NaN;
    _meanSkewness = Double.NaN;
    _stdSkewness = Double.NaN;
    _medianSkewness = Double.NaN;

    }
    return ar;
  }

  /** Loops over numeric features to get kurtosis summary for the frame **/
  public double[] kurtosis(){
    double[] ar = new double[5];
    ar[0] = _minKurtosis;
    ar[1] = _maxKurtosis;
    ar[2] = _meanKurtosis;
    ar[3] = _stdKurtosis;
    ar[4] = _medianKurtosis;
    if( _minKurtosis!=-1 && _maxKurtosis!=-1 && _meanKurtosis!=-1 && _stdKurtosis!=-1 && _medianKurtosis!=-1) return ar;

    if(isAnyNumeric()){
      int ar_size = numberOfNumericFeatures();
      double[] darry = new double[ar_size];
      int ind=0;
      for(int i=0;i<_cols.length;++i) {
        if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isNumeric) {
          darry[ind] = _cols[i]._kurtosis;
          ind +=1;
        }
      }
      Vec vd1 = dvec(darry);
      Vec[] vdd = new  Vec[1];
      vdd[0] = vd1;
      Key<Frame> key = Key.make("keyD");
      String[] names= new String[1];
      names[0] ="vd1";
      Frame dr = new Frame(key,names,vdd);
      DKV.put(dr);

      ar[0] = rapidMin(dr);
      ar[1] = rapidMax(dr);
      ar[2] = rapidMean(dr);
      ar[3] = rapidSd(dr);
      ar[4] = rapidMedian(dr);
      _minKurtosis = ar[0];
      _maxKurtosis = ar[1];
      _meanKurtosis = ar[2];
      _stdKurtosis = ar[3];
      _medianKurtosis = ar[4];
      dr.remove();
    }else{
      ar[0] = Double.NaN;
      ar[1] = Double.NaN;
      ar[2] = Double.NaN;
      ar[3] = Double.NaN;
      ar[4] = Double.NaN;
      _minKurtosis = Double.NaN;
      _maxKurtosis = Double.NaN;
      _meanKurtosis = Double.NaN;
      _stdKurtosis = Double.NaN;
      _medianKurtosis = Double.NaN;

    }
    return ar;
  }

  /** Loops over categorical features to get cardinality summary for the frame **/
  public double[] cardinality(){
    double[] ar = new double[5];
    ar[0] = _minCardinality;
    ar[1] = _maxCardinality;
    ar[2] = _meanCardinality;
    ar[3] = _stdCardinality;
    ar[4] = _medianCardinality;
    if( _minCardinality!=-1 && _maxCardinality!=-1 && _meanCardinality!=-1 && _stdCardinality!=-1 && _medianCardinality!=-1) return ar;

    if(isAnyCategorical()){
      int ar_size = numberOfCategoricalFeatures();
      double[] darry = new double[ar_size];
      int ind=0;
      for(int i=0;i<_cols.length;++i) {
        if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isCategorical) {
          darry[ind] = _cols[i]._cardinality;
          ind +=1;
        }
      }

      Vec vd1 = dvec(darry);
      Vec[] vdd = new  Vec[1];
      vdd[0] = vd1;
      Key<Frame> key = Key.make("keyD");
      String[] names= new String[1];
      names[0] ="vd1";
      Frame dr = new Frame(key,names,vdd);
      DKV.put(dr);
      ar[0] = rapidMin(dr);
      ar[1] = rapidMax(dr);
      ar[2] = rapidMean(dr);
      ar[3] = rapidSd(dr);
      ar[4] = rapidMedian(dr);
      _minCardinality = ar[0];
      _maxCardinality = ar[1];
      _meanCardinality = ar[2];
      _stdCardinality = ar[3];
      _medianCardinality = ar[4];
      dr.remove();
    }else{
      ar[0] = Double.NaN;
      ar[1] = Double.NaN;
      ar[2] = Double.NaN;
      ar[3] = Double.NaN;
      ar[4] = Double.NaN;
      _minCardinality = Double.NaN;
      _maxCardinality = Double.NaN;
      _meanCardinality = Double.NaN;
      _stdCardinality = Double.NaN;
      _medianCardinality = Double.NaN;

    }
    return ar;
  }
  /** A numeric Vec from an array of doubles */
  public static Vec dvec(double...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_NUM);
    NewChunk chunk = new NewChunk(avec, 0);
    for (double r : rows)
      chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** checks if there are any numeric features in the frame*/
  public boolean isAnyNumeric(){
    int cnt =0;
    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isNumeric) {
        cnt = 1;
        break;
      }
    }
    if(cnt ==1) return true;
    else return false;
  }

  /** checks if there are any categorical features in the frame*/
  public boolean isAnyCategorical(){
    int cnt =0;
    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isCategorical) {
        cnt = 1;
        break;
      }
    }
    if(cnt ==1) return true;
    else return false;
  }

  /** min function from rapids */
  public double rapidMin(Frame dr){
    //String y0 = String.format("(min %s)",dr._key);
    Val val = Rapids.exec("(min " + dr._key + ")");
    double d = val.getNum();
    return d;
  }

  /** max function from rapids */
  public double rapidMax(Frame dr){
    Val val = Rapids.exec("(max " + dr._key + ")");
    double d = val.getNum();
    return d;
  }

  /** mean function from rapids */
  /** AstMean now accepts a flag to treat NAs as a 0 or ignore them completely */
  public double rapidMean(Frame dr, boolean ignore_na) {
    //String y0 = String.format("(mean %s %s %s)",dr._key,1,0/1);
    Val val = Rapids.exec("(mean " + dr._key + " " + ignore_na + " false)");
    double[] darray = val.getRow();
    double d = darray[0];
    return d;
  }
  /** mean function with default of ignore_na = true */
  public double rapidMean(Frame dr) {
    return rapidMean(dr, true);
  }

  /** sd function from rapids */
  public double rapidSd(Frame dr){
    Val val = Rapids.exec("(sd " + dr._key + " true)");
    double[] darray = val.getNums();
    double d = darray[0];
    return d;
  }

  /** median function from rapids */
  public double rapidMedian(Frame dr){
    Val val = Rapids.exec("(median " + dr._key + " true)");
    double[] darray = val.getNums();
    double d = darray[0];
    return d;
  }

  /**
   * If predictors were passed, then any values computed/cached are based on those
   * predictors
   * @return
   */
  public int numberOfNumericFeatures() {
    if( _numFeat!=-1 ) return _numFeat;
    ArrayList<Integer> idxs = new ArrayList<>();
    ArrayList<Integer> intCols = new ArrayList<>();
    ArrayList<Integer> dblCols = new ArrayList<>();
    ArrayList<Integer> binCols = new ArrayList<>();
    ArrayList<Integer> intNotBinCols = new ArrayList<>();
    int cnt=0;
    int idx=0;
    for(Vec v: _fr.vecs()) {
      boolean ignored = _cols[idx]._ignored;
      boolean response= _cols[idx]._response;
      if( v.isNumeric() && !ignored && !response) {
        cnt += 1;
        idxs.add(idx);
        if( v.isInt() ) intCols.add(idx);
        if( v.isBinary() ) binCols.add(idx);
        if( v.isInt() && !v.isBinary() ) intNotBinCols.add(idx);
        if( v.isNumeric() && !v.isInt() ) dblCols.add(idx);
      }
      idx++;
    }
    _numFeats = intListToA(idxs);
    _intCols  = intListToA(intCols);
    _dblCols  = intListToA(dblCols);
    _binaryCols  = intListToA(binCols);
    _intNotBinaryCols = intListToA(intNotBinCols);
    return (_numFeat=cnt);
  }

  public int numberOfCategoricalFeatures() {
    if( _catFeat!=-1 ) return _catFeat;
    ArrayList<Integer> idxs = new ArrayList<>();
    int cnt=0;
    int idx=0;
    for(Vec v: _fr.vecs())  {
      boolean ignored = _cols[idx]._ignored;
      boolean response= _cols[idx]._response;
      if( v.isCategorical() && !ignored && !response) {
        cnt += 1;
        idxs.add(idx);
      }
      idx++;
    }
    _catFeats = intListToA(idxs);
    return (_catFeat=cnt);
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String datasetName) {
    _datasetName=datasetName;
    _fr=fr;
    _response=response;
    _cols = new ColMeta[_fr.numCols()];
    _userFeedback = userFeedback;
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String datasetName, boolean isClassification) {
    this(userFeedback, fr,response,datasetName);
    _isClassification=isClassification;
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, int[] predictors, String datasetName, boolean isClassification) {
    this(userFeedback, fr, response, intAtoStringA(predictors, fr.names()), datasetName, isClassification);
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String[] predictors, String datasetName, boolean isClassification) {
    this(userFeedback, fr, response, datasetName, isClassification);
    _includeCols = predictors;
    if( null==_includeCols )
      for (int i = 0; i < _fr.numCols(); ++i)
          _cols[i] = new ColMeta(_fr.vec(i),_fr.name(i),i,i==_response);
    else {
      HashSet<String> preds = new HashSet<>();
      Collections.addAll(preds,_includeCols);
      for(int i=0;i<_fr.numCols();++i)
        _cols[i] = new ColMeta(_fr.vec(i),_fr.name(i),i,i==_response,!preds.contains(_fr.name(i)));
    }
  }

  public boolean isClassification() { return _isClassification; }

  public String[] ignoredCols() {  // publishes private field
    if( _ignoredCols==null ) {
      ArrayList<Integer> cols = new ArrayList<>();
      for(ColMeta c: _cols)
        if( c._ignored ) cols.add(c._idx);
      _ignoredCols=new String[cols.size()];
      for(int i=0;i<cols.size();++i)
        _ignoredCols[i]=_fr.name(cols.get(i));
    }
    return _ignoredCols;
  }

  public String[] includedCols() {
    if( _includeCols==null ) {
      if( null==ignoredCols() ) return _includeCols = _fr.names();
      _includeCols = ArrayUtils.difference(_fr.names(), ignoredCols());  // clones _fr.names, so line above avoids one more copy
    }
    return _includeCols;
  }

  public ColMeta response() {
    if( -1==_response ) {
      for(int i=0;i<_cols.length;++i)
        if(_cols[i]._response) {
          _response=i; break;
        }
    }
    return _cols[_response];
  }


  public boolean stratify() { return response()._stratify; }

  public Vec[] weights() {
    if( null!=_trainTestWeight) return _trainTestWeight;
    return _trainTestWeight = stratify() ? AutoMLUtils.makeStratifiedWeights(response()._v,0.8, response()._weightMult)
                                  : AutoMLUtils.makeWeights(          response()._v,0.8, response()._weightMult);
  }

  // blocking call to compute 1st pass of column metadata
  public FrameMetadata computeFrameMetaPass1() {
    MetaPass1[] tasks = new MetaPass1[_fr.numCols()];
    for(int i=0; i<tasks.length; ++i)
      tasks[i] = new MetaPass1(i,this);
    _isClassification = tasks[_response]._isClassification;
    MetaCollector.ParallelTasks metaCollector = new MetaCollector.ParallelTasks<>(tasks);
    long start = System.currentTimeMillis();
    H2O.submitTask(metaCollector).join();
    _userFeedback.info(Stage.FeatureAnalysis,
                       "Frame metadata analyzer pass 1 completed in " +
                       (System.currentTimeMillis()-start)/1000. +
                       " seconds");
    double sumTimeToMRTaskPerCol=0;
    ArrayList<Integer> dropCols=new ArrayList<>();
    for(MetaPass1 cmt: tasks) {
      if( cmt._colMeta._ignored ) dropCols.add(cmt._colMeta._idx);
      else                        _cols[cmt._colMeta._idx] = cmt._colMeta;
      sumTimeToMRTaskPerCol+= cmt._elapsed;
    }
    _userFeedback.info(Stage.FeatureAnalysis,
                       "Average time to analyze each column: " +
                       String.format("%.5f", (sumTimeToMRTaskPerCol/tasks.length) / 1000.0) +
                       " seconds");
    if( dropCols.size()>0 )
      dropIgnoredCols(intListToA(dropCols));

    return this;
  }

  private void dropIgnoredCols(int[] dropCols) {
    _userFeedback.info(Stage.FeatureAnalysis, "AutoML dropping " + dropCols.length + " ignored columns");
    Vec[] vecsToRemove = _fr.remove(dropCols);
    for(Vec v: vecsToRemove) v.remove();
    ColMeta cm[] = new ColMeta[_fr.numCols()];
    int idx=0;
    for(int i=0;i<_fr.numCols();++i) {
      while(null==_cols[idx]) idx++;
      cm[i]=_cols[idx++];
    }
    _cols=cm;
    flushCachedItems();
  }

  private void flushCachedItems() {
    _catFeats=null;
    _numFeats=null;
    _intCols=null;
    _dblCols=null;
    _binaryCols=null;
    _intNotBinaryCols=null;
    _response=-1;
    _naCnt=-1;
    _numFeat=-1;
    _catFeat=-1;
    _nclass=-1;
    _ignoredCols=null;
    _includeCols=null;
    _featsWithNa=-1;
    _rowsWithNa=-1;
    _minKurtosis=-1;
    _minSkewness=-1;
    _minCardinality=-1;
    _maxKurtosis=-1;
    _maxSkewness=-1;
    _maxCardinality=-1;
    _meanKurtosis=-1;
    _meanSkewness=-1;
    _meanCardinality=-1;
    _stdKurtosis=-1;
    _stdSkewness=-1;
    _stdCardinality=-1;
    _medianKurtosis=-1;
    _medianSkewness=-1;
    _medianCardinality=-1;


  }

  public static String[] intAtoStringA(int[] select, String[] names) {
    String[] preds = new String[select.length];
    int i=0;
    for(int p: select) preds[i++] = names[p];
    return preds;
  }

  private static class MetaPass1 extends H2O.H2OCountedCompleter<MetaPass1> {
    private final boolean _response;   // compute class distribution & more granular histo
    private boolean _isClassification; // is this a classification problem?
    private double _mean;              // mean of the column, passed
    private final ColMeta _colMeta;    // result; also holds onto the DHistogram
    private long _elapsed;             // time to mrtask
    static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }
    public MetaPass1(int idx, FrameMetadata fm) {
      Vec v = fm._fr.vec(idx);
      _response=fm._response==idx;
      String colname = fm._fr.name(idx);
        _colMeta = new ColMeta(v, colname, idx, _response);
      if( _response ) _isClassification = _colMeta.isClassification();
      _mean = v.mean();
      if(v.isCategorical()){
        _colMeta._cardinality = v.cardinality();
      }else{
        _colMeta._cardinality = 0;
      }

      int nbins = (int) Math.ceil(1 + log2(v.length()));  // Sturges nbins
      int xbins = (char) ((long) v.max() - (long) v.min());

      if(!(_colMeta._ignored) && !(_colMeta._v.isBad()) && xbins > 0) {
        _colMeta._histo = MetaCollector.DynamicHisto.makeDHistogram(colname, nbins, nbins, (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0)), v.min(), v.max());
      }

      //skewness, kurtosis using rapids call
      Key<Frame> key = Key.make("keyW");
      String[] names= new String[1];
      names[0] = "num1";
      Vec[] vecs = new  Vec[1];
      vecs[0] = v;
      Frame vec_tofr = new Frame(key,names, vecs);
      DKV.put(vec_tofr);
      //Skewness
      String x = String.format("(skewness %s %s )",vec_tofr._key,true);
      Val res1 = Rapids.exec(x);
      double[] darray1 = res1.getNums();
      _colMeta._skew = darray1[0];
      //Kurtosis
      String y = String.format("(kurtosis %s %s )",vec_tofr._key,true);
      Val res2 = Rapids.exec(y);
      double[] darray2 = res2.getNums();
      _colMeta._kurtosis = darray2[0];
      DKV.remove(vec_tofr._key);
      //vec_tofr.remove();
    }

    public ColMeta meta() { return _colMeta; }
    public long elapsed() { return _elapsed; }

    @Override public void compute2() {
      long start = System.currentTimeMillis();
      int xbins = (char) ((long) _colMeta._v.max() - (long) _colMeta._v.min());
      if (!(_colMeta._ignored) && !(_colMeta._v.isBad()) && xbins > 0) {
        HistTask t = new HistTask(_colMeta._histo, _mean).doAll(_colMeta._v);
        _elapsed = System.currentTimeMillis() - start;
        _colMeta._thirdMoment = t._thirdMoment / ((_colMeta._v.length() - _colMeta._v.naCnt()) - 1);
        _colMeta._fourthMoment = t._fourthMoment / ((_colMeta._v.length() - _colMeta._v.naCnt()) - 1);
        _colMeta._MRTaskMillis = _elapsed;
        Log.info("completed MetaPass1 for col number: " + _colMeta._idx);
        //_colMeta._skew = _colMeta._thirdMoment / Math.sqrt(_colMeta._variance*_colMeta._variance*_colMeta._variance);
        //_colMeta._kurtosis = _colMeta._fourthMoment / (_colMeta._variance * _colMeta._variance);
      }
      tryComplete();
    }

    private static class HistTask extends MRTask<HistTask> {
      private DHistogram _h;
      private double _thirdMoment;     // used for skew/kurtosis; NaN if not numeric
      private double _fourthMoment;    // used for skew/kurtosis; NaN if not numeric
      private double _mean;

      HistTask(DHistogram h, double mean) { _h = h; _mean=mean; }
      @Override public void setupLocal() { _h.init(); }
      @Override public void map( Chunk C ) {
        double min = _h.find_min();
        double max = _h.find_maxIn();
        double[] bins = new double[_h._nbin];
        double colData;
        for(int r=0; r<C._len; ++r) {
          if( Double.isNaN(colData=C.atd(r)) ) continue;
          if( colData < min ) min = colData;
          if( colData > max ) max = colData;
          bins[_h.bin(colData)]++;          double delta = colData - _mean;
          double threeDelta = delta*delta*delta;
          _thirdMoment  += threeDelta;
          _fourthMoment += threeDelta*delta;
        }
        _h.setMin(min); _h.setMaxIn(max);
        for(int b=0; b<bins.length; ++b)
          if( bins[b]!=0 )
            _h.addWAtomic(b, bins[b]);
      }

      @Override public void reduce(HistTask t) {
        if( _h==t._h ) return;
        if( _h==null ) _h=t._h;
        else if( t._h!=null )
          _h.add(t._h);

        if( !Double.isNaN(t._thirdMoment) ) {
          if( Double.isNaN(_thirdMoment) ) _thirdMoment = t._thirdMoment;
          else _thirdMoment += t._thirdMoment;
        }

        if( !Double.isNaN(t._fourthMoment) ) {
          if( Double.isNaN(_fourthMoment) ) _fourthMoment = t._fourthMoment;
          else _fourthMoment += t._fourthMoment;
        }
      }
    }
  }
}
