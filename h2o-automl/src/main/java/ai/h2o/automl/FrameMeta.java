package ai.h2o.automl;

import ai.h2o.automl.collectors.MetaCollector;
import ai.h2o.automl.guessers.ProblemTypeGuesser;
import hex.tree.DHistogram;
import water.H2O;
import water.Iced;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.AtomicUtils;

import java.util.ArrayList;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMeta extends Iced {
  final Frame _fr;
  private final int _response;
  ColMeta[] _cols;

  private boolean _isClassification;

  // cached things
  private String[] _ignoredCols;

  public FrameMeta(Frame fr, int response) {
    _fr=fr;
    _response=response;
    _cols = new ColMeta[_fr.numCols()];
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

    @Override protected void compute2() {
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
