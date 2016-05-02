package ai.h2o.automl.guessers.column;

import ai.h2o.automl.colmeta.ColMeta;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;


public class SpecialNAGuesser extends Guesser {
  public SpecialNAGuesser(ColMeta cm) { super(cm); }
  @Override public void guess0(String name, Vec v) {
    _cm._specialNAs = new SpecialNA(v.isNumeric()
            ? (v.isInt() ? SpecialNA.INT : SpecialNA.DBL)
            : SpecialNA.STR
    ).scan(v,_cm);
  }

  // stupid wrapper class for possibly special types of NAs; things like 999999 or -1 or 0
// https://0xdata.atlassian.net/browse/STEAM-76
  public static class SpecialNA extends Iced {
    int[] _ints;
    double[] _dbls;
    String[] _strs;

    private transient HashSet<Double> _nas;

    byte _type;
    int _idx;
    public static final byte INT=0;
    public static final byte DBL=1;
    public static final byte STR=2;

    public String typeToString() {
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

    public SpecialNA(byte type) {
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
        median = median(v, QuantileModel.CombineMethod.INTERPOLATE);
        double mean = v.mean();
        double skew = Math.abs(mean-median);
        if( Math.abs(skew) > 700 ) {  // strong skew => maybe odd NA encoding as large/small value
          Log.info("Found a strong skew due to possible NA encoded as integer. Median=" + median + "; mean=" + mean);
          Log.info("Checking if NAs encoded as -99999 or 99999 or some variant.");
          int l = v.pctiles().length-1;
          int min = (int)v.pctiles()[0];
          int max = (int)v.pctiles()[l];

          // check if the pctiles are screwed by data distribution
          if( min==max ) {

          }
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

  public static double median(Vec v, QuantileModel.CombineMethod combine_method) {
    return median(new Frame(v),combine_method);
  }
}