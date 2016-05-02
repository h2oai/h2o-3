package ai.h2o.automl;

import ai.h2o.automl.autocollect.AutoCollect;
import ai.h2o.automl.collectors.MetaCollector;
import ai.h2o.automl.guessers.ProblemTypeGuesser;
import ai.h2o.automl.tasks.DummyClassifier;
import ai.h2o.automl.tasks.DummyRegressor;
import ai.h2o.automl.tasks.VIF;
import hex.tree.DHistogram;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.Log;

import java.util.*;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMeta extends Iced {
  final String _datasetName;
  public final Frame _fr;
  public int[] _catFeats;
  public int[] _numFeats;
  public int[] _intCols;
  public int[] _dblCols;
  public int[] _binaryCols;
  public int[] _intNotBinaryCols;
  final int _response;
  private long _naCnt=-1;  // count of nas across whole frame
  private int _numFeat=-1; // count of numerical features
  private int _catFeat=-1; // count of categorical features
  private int _nclass=-1;  // number of classes if classification problem
  private double[][] _dummies=null; // dummy predictions
  public ColMeta[] _cols;

  public final static String[] METAVALUES = new String[]{
    "DatasetName", "NRow", "NCol", "LogNRow", "LogNCol", "NACount", "NAFraction",
    "NumberNumericFeat", "NumberCatFeat", "RatioNumericToCatFeat", "RatioCatToNumericFeat",
    "DatasetRatio", "LogDatasetRatio", "InverseDatasetRatio", "LogInverseDatasetRatio",
    "Classification", "DummyStratMSE", "DummyStratLogLoss", "DummyMostFreqMSE",
    "DummyMostFreqLogLoss", "DummyRandomMSE", "DummyRandomLogLoss", "DummyMedianMSE",
    "DummyMeanMSE", "NClass"};

  public static HashMap<String, Object> makeEmptyFrameMeta() {
    HashMap<String,Object> hm = new HashMap<>();
    for(String key: FrameMeta.METAVALUES) hm.put(key,null);
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
    fm.put("NACount", (double)naCount());
    fm.put("NAFraction", (double) naCount() / (double) (_fr.numCols() * _fr.numRows()));
    fm.put("NumberNumericFeat", (double)numberOfNumericFeatures());
    fm.put("NumberCatFeat", (double) numberOfCategoricalFeatures());
    fm.put("RatioNumericToCatFeat", Double.isInfinite((double) fm.get("NumberCatFeat"))     ? AutoCollect.SQLNAN : (double) fm.get("NumberNumericFeat") / (double) fm.get("NumberCatFeat"));
    fm.put("RatioCatToNumericFeat", Double.isInfinite((double) fm.get("NumberNumericFeat")) ? AutoCollect.SQLNAN : (double) fm.get("NumberCatFeat")     / (double) fm.get("NumberNumericFeat"));
    fm.put("DatasetRatio", (double) _fr.numCols() / (double) _fr.numRows());
    fm.put("LogDatasetRatio", Math.log((double) fm.get("DatasetRatio")));
    fm.put("InverseDatasetRatio", (double)_fr.numRows() / (double) _fr.numCols() );
    fm.put("LogInverseDatasetRatio", Math.log((double)fm.get("InverseDatasetRatio")));
    fm.put("Classification", _isClassification?1:0);
  }

  public void fillDummies(HashMap<String, Object> fm) {
    double[][] dummies = getDummies();
    fm.put("DummyStratMSE", _isClassification?dummies[0][0]: AutoCollect.SQLNAN);
    fm.put("DummyStratLogLoss", _isClassification?dummies[1][0]: AutoCollect.SQLNAN);
    fm.put("DummyMostFreqMSE", _isClassification?dummies[0][2]: AutoCollect.SQLNAN);
    fm.put("DummyMostFreqLogLoss", _isClassification?dummies[1][2]: AutoCollect.SQLNAN);
    fm.put("DummyRandomMSE", _isClassification?dummies[0][1]: AutoCollect.SQLNAN);
    fm.put("DummyRandomLogLoss", _isClassification?dummies[1][1]: AutoCollect.SQLNAN);
    fm.put("DummyMedianMSE", _isClassification? AutoCollect.SQLNAN:dummies[0][1]);
    fm.put("DummyMeanMSE", _isClassification? AutoCollect.SQLNAN:dummies[0][0]);
    fm.put("NClass", _nclass);
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

  public long naCount() {
    if( _naCnt!=-1 ) return _naCnt;
    long cnt=0;
    for(Vec v: _fr.vecs()) cnt+=v.naCnt();
    return (_naCnt=cnt);
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

  public static int[] intListToA(List<Integer> list) {
    int[] a=new int[0];
    if( list.size() >0 ) {
      a = new int[list.size()];
      for(int i=0;i<a.length;++i) a[i] = list.get(i);
    }
    return a;
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

  public double[][] getDummies() {
    if( _dummies!=null ) return _dummies;
    DummyClassifier dc=null;
    _dummies = _isClassification
            ? DummyClassifier.getDummies(dc=new DummyClassifier(_fr.vec(_response), new String[]{"mse", "logloss"}), _fr.vec(_response), null)
            : DummyRegressor.getDummies(_fr.vec(_response), null, new String[]{"mse"});
    assert (_isClassification && dc!=null) || !_isClassification;
    _nclass = _isClassification?dc._nclass:1;
    return _dummies;
  }

  private boolean _isClassification;

  // cached things
  private String[] _ignoredCols;
  private String[] _includeCols;

  public FrameMeta(Frame fr, int response, String datasetName) {
    _datasetName=datasetName;
    _fr=fr;
    _response=response;
    _cols = new ColMeta[_fr.numCols()];
  }

  public FrameMeta(Frame fr, int response, String datasetName, boolean isClassification) {
    this(fr,response,datasetName);
    _isClassification=isClassification;
  }

  public FrameMeta(Frame fr, int response, int[] predictors, String datasetName, boolean isClassification) {
    this(fr, response, intAtoStringA(predictors, fr.names()), datasetName, isClassification);
  }

  public FrameMeta(Frame fr, int response, String[] predictors, String datasetName, boolean isClassification) {
    this(fr, response, datasetName, isClassification);
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
    if( _includeCols==null )
      _includeCols = ArrayUtils.difference(_fr.names(), ignoredCols());
    return _includeCols;
  }

  public ColMeta response() { return _cols[_response]; }

  // blocking call to compute 1st pass of column metadata
  public FrameMeta computeFrameMetaPass1() {
    MetaPass1[] tasks = new MetaPass1[_fr.numCols()];
    for(int i=0; i<tasks.length; ++i)
      tasks[i] = new MetaPass1(i,this);
    _isClassification = tasks[_response]._isClassification;
    MetaCollector.ParallelTasks metaCollector = new MetaCollector.ParallelTasks<>(tasks);
    long start = System.currentTimeMillis();
    H2O.submitTask(metaCollector).join();
    Log.info("MetaPass1 completed in " + (System.currentTimeMillis()-start)/1000. + " seconds");
    double sumTimeToMRTaskPerCol=0;
    for(MetaPass1 cmt: tasks) {
      _cols[cmt._colMeta._idx] = cmt._colMeta;
      sumTimeToMRTaskPerCol+= cmt._elapsed;
    }
    Log.info("Average time to MRTask per column: "+ ((sumTimeToMRTaskPerCol)/(tasks.length))/1000. + " seconds");
    return this;
  }

  public FrameMeta computeVIFs() {
    VIF[] vifs = VIF.make(_fr._key, includedCols(), _fr.names());
    VIF.launchVIFs(vifs);
    int i=0;
    for( String col: includedCols() ) {
      _cols[Arrays.asList(_fr.names()).indexOf(col)]._vif = vifs[i++].vif();
    }
    return this;
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
    public MetaPass1(int idx, FrameMeta fm) {
      Vec v = fm._fr.vec(idx);
      _response=fm._response==idx;
      String colname = fm._fr.name(idx);
      if( _response ) {
        if( _isClassification = ProblemTypeGuesser.guess(v)) {
          Vec deleteMe=null;
          if( !v.isCategorical() )
            deleteMe=fm._fr.replace(idx,(v=v.toCategoricalVec()));
          if( deleteMe!=null ) {
            DKV.put(fm._fr);
            deleteMe.remove();
          }
        }
      }
      _colMeta = new ColMeta(v, colname, idx, _response);
      _mean = v.mean();
      int nbins = (int) Math.ceil(1 + log2(v.length()));  // Sturges nbins
      _colMeta._histo = MetaCollector.DynamicHisto.makeDHistogram(colname, nbins, nbins, (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0)), v.min(), v.max());
    }

    public ColMeta meta() { return _colMeta; }
    public long elapsed() { return _elapsed; }

    @Override public void compute2() {
      long start = System.currentTimeMillis();
      HistTask  t = new HistTask(_colMeta._histo, _mean).doAll(_colMeta._v);
      _elapsed = System.currentTimeMillis() - start;
      _colMeta._thirdMoment = t._thirdMoment / ((_colMeta._v.length() - _colMeta._v.naCnt())-1);
      _colMeta._fourthMoment = t._fourthMoment / ((_colMeta._v.length() - _colMeta._v.naCnt())-1);
      _colMeta._MRTaskMillis = _elapsed;
      _colMeta._skew = _colMeta._thirdMoment / Math.sqrt(_colMeta._variance*_colMeta._variance*_colMeta._variance);
      _colMeta._kurtosis = _colMeta._fourthMoment / (_colMeta._variance * _colMeta._variance);
      tryComplete();
    }

    private static class HistTask extends MRTask<HistTask> {
      private DHistogram _h;
      private double _thirdMoment;     // used for skew/kurtosis; NaN if not numeric
      private double _fourthMoment;    // used for skew/kurtosis; NaN if not numeric
      private double _mean;

      HistTask(DHistogram h, double mean) { _h = h; _mean=mean; }
      @Override public void setupLocal() { _h._bins = MemoryManager.malloc8d(_h._nbin); }
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
        _h.setMin(min); _h.setMax(max);
        for(int b=0; b<bins.length; ++b)
          if( bins[b]!=0 )
            AtomicUtils.DoubleArray.add(_h._bins, b, bins[b]);
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
