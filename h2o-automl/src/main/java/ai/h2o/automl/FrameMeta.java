package ai.h2o.automl;

import ai.h2o.automl.collectors.MetaCollector;
import ai.h2o.automl.guessers.ProblemTypeGuesser;
import ai.h2o.automl.tasks.DummyClassifier;
import ai.h2o.automl.tasks.DummyRegressor;
import ai.h2o.automl.tasks.VIF;
import hex.tree.DHistogram;
import water.H2O;
import water.Iced;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMeta extends Iced {
  final String _datasetName;
  final Frame _fr;
  private final int _response;
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
    fm.put("RatioNumericToCatFeat", (double) fm.get("NumberNumericFeat") / (double) fm.get("NumberCatFeat"));
    fm.put("RatioCatToNumericFeat", (double) fm.get("NumberCatFeat") / (double) fm.get("NumberNumericFeat"));
    fm.put("DatasetRatio", (double) _fr.numCols() / (double) _fr.numRows());
    fm.put("LogDatasetRatio", Math.log((double) fm.get("DatasetRatio")));
    fm.put("InverseDatasetRatio", (double)_fr.numRows() / (double) _fr.numCols() );
    fm.put("LogInverseDatasetRatio", Math.log((double)fm.get("InverseDatasetRatio")));
    fm.put("Classification", _isClassification?1:0);
  }

  public void fillDummies(HashMap<String, Object> fm) {
    double[][] dummies = getDummies();
    fm.put("DummyStratMSE", _isClassification?dummies[0][0]:AutoML.SQLNAN);
    fm.put("DummyStratLogLoss", _isClassification?dummies[1][0]:AutoML.SQLNAN);
    fm.put("DummyMostFreqMSE", _isClassification?dummies[0][2]:AutoML.SQLNAN);
    fm.put("DummyMostFreqLogLoss", _isClassification?dummies[1][2]:AutoML.SQLNAN);
    fm.put("DummyRandomMSE", _isClassification?dummies[0][1]:AutoML.SQLNAN);
    fm.put("DummyRandomLogLoss", _isClassification?dummies[1][1]:AutoML.SQLNAN);
    fm.put("DummyMedianMSE", _isClassification?AutoML.SQLNAN:dummies[0][1]);
    fm.put("DummyMeanMSE", _isClassification?AutoML.SQLNAN:dummies[0][0]);
    fm.put("NClass", _nclass);
  }

  public long naCount() {
    if( _naCnt!=-1 ) return _naCnt;
    long cnt=0;
    for(Vec v: _fr.vecs()) cnt+=v.naCnt();
    return (_naCnt=cnt);
  }

  public int numberOfNumericFeatures() {
    if( _numFeat!=-1 ) return _numFeat;
    int cnt=0;
    for(Vec v: _fr.vecs()) cnt+= v.isNumeric() ? 1 : 0;
    return (_numFeat=cnt);
  }

  public int numberOfCategoricalFeatures() {
    if( _catFeat!=-1 ) return _catFeat;
    int cnt=0;
    for(Vec v: _fr.vecs()) cnt+= v.isCategorical() ? 1 : 0;
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
      tasks[i] = new MetaPass1(i==_response, _fr.vec(i), _fr.name(i), i);
    _isClassification = tasks[_response]._isClassification;
    MetaCollector.ParallelTasks metaCollector = new MetaCollector.ParallelTasks<>(tasks);
    H2O.submitTask(metaCollector).join();
    for(MetaPass1 cmt: tasks)
      _cols[cmt._colMeta._idx] = cmt._colMeta;
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
    private final double _mean;        // mean of the column, passed
    private final ColMeta _colMeta;    // result; also holds onto the DHistogram
    private long _elapsed;             // time to mrtask

    static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }
    public MetaPass1(boolean response, Vec v, String colname, int idx) {
      _mean = v.mean();
      _colMeta = new ColMeta(v, colname, idx, _response=response);
      int nbins = (int)Math.ceil(1 + log2(v.length()));  // Sturges nbins
      _colMeta._histo = MetaCollector.DynamicHisto.makeDHistogram(colname, nbins, nbins, (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0)), v.min(), v.max());
      if( _response )
        _isClassification = ProblemTypeGuesser.guess(v);
    }

    public ColMeta meta() { return _colMeta; }
    public long elapsed() { return _elapsed; }

    @Override public void compute2() {
      long start = System.currentTimeMillis();
      HistTask t = new HistTask(_colMeta._histo, _mean).doAll(_colMeta._v);
      _elapsed = System.currentTimeMillis() - start;
      _colMeta._thirdMoment = t._thirdMoment / (_colMeta._v.length()-1);
      _colMeta._fourthMoment = t._fourthMoment / (_colMeta._v.length()-1);
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
          colData=C.atd(r);
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
        _thirdMoment+=t._thirdMoment;
        _fourthMoment+= t._fourthMoment;
      }
    }
  }
}
